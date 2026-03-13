import React, { useEffect, useState } from "react";
import {
  ActivityIndicator,
  FlatList,
  Pressable,
  SafeAreaView,
  StyleSheet,
  Text,
  View
} from "react-native";
import { Avatar } from "../components/Avatar";
import { api } from "../services/api";
import { localDatabase } from "../services/localDatabase";
import { formatPresenceStatus } from "../services/presence";
import { useAppStore } from "../store/useAppStore";
import type { ChatSummary } from "../types";

type ChatsListScreenProps = {
  onOpenChat: (chat: ChatSummary) => void;
  onOpenGlobalSearch: () => void;
  onOpenCalls: () => void;
  onOpenSecretChats: () => void;
  onCreateDirect: () => void;
  onCreateGroup: () => void;
  onCreateChannel: () => void;
  onOpenJoinByLink: () => void;
  onOpenStories: () => void;
  onCreateStory: () => void;
  onOpenContacts: () => void;
  onOpenFolders: () => void;
  onOpenProfile: () => void;
  onOpenArchived: () => void;
  onOpenSavedMessages: () => void;
};

export function ChatsListScreen({
  onOpenChat,
  onOpenGlobalSearch,
  onOpenCalls,
  onOpenSecretChats,
  onCreateDirect,
  onCreateGroup,
  onCreateChannel,
  onOpenJoinByLink,
  onOpenStories,
  onCreateStory,
  onOpenContacts,
  onOpenFolders,
  onOpenProfile,
  onOpenArchived,
  onOpenSavedMessages
}: ChatsListScreenProps) {
  const session = useAppStore((state) => state.session);
  const chats = useAppStore((state) => state.chats);
  const folders = useAppStore((state) => state.folders);
  const setChats = useAppStore((state) => state.setChats);
  const setFolders = useAppStore((state) => state.setFolders);
  const logout = useAppStore((state) => state.logout);

  const [refreshing, setRefreshing] = useState(false);
  const [signingOut, setSigningOut] = useState(false);
  const [selectedFolderId, setSelectedFolderId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function loadChats(token: string) {
    if (!session) {
      return;
    }
    setRefreshing(true);
    setError(null);
    try {
      const [nextChats, nextFolders] = await Promise.all([
        api.getChats(token),
        api.getFolders(token)
      ]);
      setChats(nextChats);
      setFolders(nextFolders);
      await Promise.all([
        localDatabase.replaceChats(session.userId, nextChats),
        localDatabase.replaceFolders(session.userId, nextFolders)
      ]);
    } catch (loadError) {
      setError(
        chats.length > 0 || folders.length > 0
          ? "Offline mode. Showing cached chats."
          : loadError instanceof Error
            ? loadError.message
            : "Unable to load chats"
      );
    } finally {
      setRefreshing(false);
    }
  }

  function formatAutoDelete(seconds: number | null) {
    if (!seconds) {
      return null;
    }
    if (seconds < 60) {
      return `TTL ${seconds}s`;
    }
    if (seconds < 3600) {
      return `TTL ${Math.round(seconds / 60)}m`;
    }
    if (seconds < 86400) {
      return `TTL ${Math.round(seconds / 3600)}h`;
    }
    return `TTL ${Math.round(seconds / 86400)}d`;
  }

  function formatChatMeta(chat: ChatSummary) {
    if (chat.chatType === "DIRECT") {
      return [
        chat.peerIsBot
          ? "bot"
          : formatPresenceStatus(
              { online: chat.peerOnline, lastSeenAt: chat.peerLastSeenAt },
              "status hidden"
            ),
        chat.peerBotSupportsInline ? "inline" : null,
        chat.peerPhoneNumber ?? "phone-hidden"
      ]
        .filter(Boolean)
        .join(" - ");
    }
    if (chat.chatType === "SAVED") {
      return "private notes";
    }

    const parts = [
      chat.publicUsername ? `@${chat.publicUsername}` : null,
      `${chat.memberCount} members`,
      chat.forumEnabled ? `${chat.topicCount} topic${chat.topicCount === 1 ? "" : "s"}` : null
    ].filter(Boolean);

    return parts.join(" - ");
  }

  useEffect(() => {
    if (!session) {
      return;
    }

    void Promise.all([
      localDatabase.getChats(session.userId),
      localDatabase.getFolders(session.userId)
    ])
      .then(([cachedChats, cachedFolders]) => {
        if (cachedChats.length > 0) {
          setChats(cachedChats);
        }
        if (cachedFolders.length > 0) {
          setFolders(cachedFolders);
        }
      })
      .catch(() => undefined);

    void loadChats(session.token);
  }, [session, setChats, setFolders]);

  if (!session) {
    return null;
  }

  async function handleLogout() {
    if (!session) {
      return;
    }
    setSigningOut(true);
    try {
      await api.revokeSession(session.token, session.sessionId);
    } catch {
    } finally {
      logout();
      setSigningOut(false);
    }
  }

  const displayedChats = selectedFolderId
    ? chats.filter((chat) =>
        folders.find((folder) => folder.folderId === selectedFolderId)?.chatIds.includes(chat.chatId)
      )
    : chats;

  return (
    <SafeAreaView style={styles.screen}>
      <View style={styles.header}>
        <View>
          <Text style={styles.title}>Chats</Text>
          <Text style={styles.subtitle}>
            {session.displayName} - {session.phoneNumber}
          </Text>
        </View>
        <Pressable
          disabled={signingOut}
          onPress={() => void handleLogout()}
          style={[styles.secondaryButton, signingOut && styles.disabled]}
        >
          <Text style={styles.secondaryButtonText}>{signingOut ? "..." : "Sign out"}</Text>
        </Pressable>
      </View>

      <View style={styles.actionsRow}>
        <Pressable onPress={onOpenGlobalSearch} style={styles.secondaryActionButton}>
          <Text style={styles.secondaryActionText}>Search</Text>
        </Pressable>
        <Pressable onPress={onCreateDirect} style={styles.secondaryActionButton}>
          <Text style={styles.secondaryActionText}>New direct</Text>
        </Pressable>
        <Pressable onPress={onCreateGroup} style={styles.secondaryActionButton}>
          <Text style={styles.secondaryActionText}>New group</Text>
        </Pressable>
        <Pressable onPress={onCreateChannel} style={styles.secondaryActionButton}>
          <Text style={styles.secondaryActionText}>New channel</Text>
        </Pressable>
        <Pressable onPress={onOpenJoinByLink} style={styles.secondaryActionButton}>
          <Text style={styles.secondaryActionText}>Join link</Text>
        </Pressable>
      </View>

      <View style={styles.actionsRow}>
        <Pressable onPress={onOpenStories} style={styles.secondaryActionButton}>
          <Text style={styles.secondaryActionText}>Stories</Text>
        </Pressable>
        <Pressable onPress={onCreateStory} style={styles.secondaryActionButton}>
          <Text style={styles.secondaryActionText}>New story</Text>
        </Pressable>
      </View>

      <View style={styles.actionsRow}>
        <Pressable onPress={onOpenSavedMessages} style={styles.secondaryActionButton}>
          <Text style={styles.secondaryActionText}>Saved</Text>
        </Pressable>
        <Pressable onPress={onOpenContacts} style={styles.secondaryActionButton}>
          <Text style={styles.secondaryActionText}>Contacts</Text>
        </Pressable>
        <Pressable onPress={onOpenCalls} style={styles.secondaryActionButton}>
          <Text style={styles.secondaryActionText}>Calls</Text>
        </Pressable>
        <Pressable onPress={onOpenSecretChats} style={styles.secondaryActionButton}>
          <Text style={styles.secondaryActionText}>Secret</Text>
        </Pressable>
      </View>

      <View style={styles.actionsRow}>
        <Pressable onPress={onOpenArchived} style={styles.secondaryActionButton}>
          <Text style={styles.secondaryActionText}>Archived</Text>
        </Pressable>
        <Pressable onPress={onOpenProfile} style={styles.secondaryActionButton}>
          <Text style={styles.secondaryActionText}>Profile</Text>
        </Pressable>
      </View>

      <View style={styles.actionsRow}>
        <Pressable
          onPress={() => setSelectedFolderId(null)}
          style={[styles.folderChip, !selectedFolderId && styles.folderChipActive]}
        >
          <Text style={[styles.folderChipText, !selectedFolderId && styles.folderChipTextActive]}>
            All
          </Text>
        </Pressable>
        {folders.map((folder) => (
          <Pressable
            key={folder.folderId}
            onPress={() => setSelectedFolderId(folder.folderId)}
            style={[styles.folderChip, selectedFolderId === folder.folderId && styles.folderChipActive]}
          >
            <Text
              style={[
                styles.folderChipText,
                selectedFolderId === folder.folderId && styles.folderChipTextActive
              ]}
            >
              {folder.title}
            </Text>
          </Pressable>
        ))}
        <Pressable onPress={onOpenFolders} style={styles.manageFoldersButton}>
          <Text style={styles.manageFoldersText}>Folders</Text>
        </Pressable>
      </View>

      {refreshing ? <ActivityIndicator color="#0f172a" style={styles.loader} /> : null}
      {error ? <Text style={styles.errorText}>{error}</Text> : null}

      <FlatList
        contentContainerStyle={styles.listContent}
        data={displayedChats}
        keyExtractor={(item) => item.chatId}
        ListEmptyComponent={
          <Text style={styles.emptyState}>
            No dialogs yet. Create the first direct message through the backend API or from another client.
          </Text>
        }
        renderItem={({ item }) => (
          <Pressable onPress={() => onOpenChat(item)} style={styles.chatCard}>
            <Avatar uri={item.photoUrl} title={item.title} size={56} />
            <View style={styles.chatBody}>
              <View style={styles.chatTopRow}>
                <Text style={styles.chatName}>{item.title}</Text>
                {item.forumEnabled ? (
                  <View style={styles.forumBadge}>
                    <Text style={styles.forumBadgeText}>Forum</Text>
                  </View>
                ) : null}
                {item.mutedUntil && new Date(item.mutedUntil).getTime() > Date.now() ? (
                  <View style={styles.mutedBadge}>
                    <Text style={styles.mutedBadgeText}>Muted</Text>
                  </View>
                ) : null}
                {item.unreadCount > 0 ? (
                  <View style={styles.unreadBadge}>
                    <Text style={styles.unreadBadgeText}>{item.unreadCount}</Text>
                  </View>
                ) : null}
                {item.mentionCount > 0 ? (
                  <View style={styles.mentionBadge}>
                    <Text style={styles.mentionBadgeText}>@{item.mentionCount}</Text>
                  </View>
                ) : null}
                {item.replyCount > 0 ? (
                  <View style={styles.replyBadge}>
                    <Text style={styles.replyBadgeText}>Reply {item.replyCount}</Text>
                  </View>
                ) : null}
              </View>
              <Text style={styles.chatMeta}>{formatChatMeta(item)}</Text>
              {item.autoDeleteSeconds ? (
                <Text style={styles.chatTimer}>{formatAutoDelete(item.autoDeleteSeconds)}</Text>
              ) : null}
              {item.draftText ? (
                <Text numberOfLines={1} style={styles.chatDraft}>
                  Draft: {item.draftText}
                </Text>
              ) : item.about ? (
                <Text numberOfLines={1} style={styles.chatMeta}>
                  {item.about}
                </Text>
              ) : null}
              <Text style={styles.chatMeta}>
                {new Date(item.lastMessageAt).toLocaleString()}
              </Text>
            </View>
          </Pressable>
        )}
      />

      <Pressable
        onPress={() => void loadChats(session.token)}
        style={[styles.primaryButton, styles.refreshButton]}
      >
        <Text style={styles.primaryButtonText}>Refresh chats</Text>
      </Pressable>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: "#f8fafc",
    padding: 20
  },
  header: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: 16
  },
  title: {
    fontSize: 28,
    fontWeight: "700",
    color: "#0f172a"
  },
  subtitle: {
    marginTop: 4,
    fontSize: 14,
    color: "#475569"
  },
  primaryButton: {
    borderRadius: 14,
    backgroundColor: "#0f172a",
    paddingVertical: 14,
    alignItems: "center"
  },
  primaryButtonText: {
    color: "#ffffff",
    fontSize: 16,
    fontWeight: "600"
  },
  secondaryButton: {
    paddingHorizontal: 14,
    paddingVertical: 10,
    borderRadius: 12,
    backgroundColor: "#e2e8f0"
  },
  secondaryButtonText: {
    color: "#0f172a",
    fontWeight: "600"
  },
  disabled: {
    opacity: 0.6
  },
  actionsRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 12,
    marginBottom: 16
  },
  secondaryActionButton: {
    minWidth: 104,
    flexGrow: 1,
    borderRadius: 14,
    backgroundColor: "#e2e8f0",
    paddingVertical: 12,
    alignItems: "center"
  },
  secondaryActionText: {
    color: "#0f172a",
    fontWeight: "600"
  },
  folderChip: {
    borderRadius: 999,
    backgroundColor: "#e2e8f0",
    paddingHorizontal: 12,
    paddingVertical: 9
  },
  folderChipActive: {
    backgroundColor: "#0f172a"
  },
  folderChipText: {
    color: "#0f172a",
    fontWeight: "600"
  },
  folderChipTextActive: {
    color: "#ffffff"
  },
  manageFoldersButton: {
    borderRadius: 999,
    backgroundColor: "#dbeafe",
    paddingHorizontal: 12,
    paddingVertical: 9
  },
  manageFoldersText: {
    color: "#1d4ed8",
    fontWeight: "700"
  },
  errorText: {
    color: "#b91c1c",
    fontSize: 14
  },
  loader: {
    marginBottom: 12
  },
  listContent: {
    gap: 12,
    paddingBottom: 20
  },
  chatCard: {
    borderRadius: 18,
    backgroundColor: "#ffffff",
    padding: 16,
    flexDirection: "row",
    gap: 14,
    alignItems: "center"
  },
  chatBody: {
    flex: 1,
    gap: 4
  },
  chatTopRow: {
    flexDirection: "row",
    alignItems: "center",
    flexWrap: "wrap",
    gap: 12
  },
  forumBadge: {
    borderRadius: 999,
    backgroundColor: "#dbeafe",
    paddingHorizontal: 8,
    paddingVertical: 4
  },
  forumBadgeText: {
    color: "#1d4ed8",
    fontSize: 11,
    fontWeight: "700"
  },
  chatName: {
    fontSize: 18,
    fontWeight: "600",
    color: "#0f172a",
    flex: 1
  },
  chatMeta: {
    fontSize: 13,
    color: "#64748b"
  },
  chatDraft: {
    fontSize: 13,
    color: "#b45309",
    fontWeight: "600"
  },
  chatTimer: {
    fontSize: 12,
    color: "#0f766e",
    fontWeight: "700"
  },
  unreadBadge: {
    minWidth: 28,
    borderRadius: 999,
    backgroundColor: "#0f766e",
    paddingHorizontal: 8,
    paddingVertical: 5,
    alignItems: "center"
  },
  unreadBadgeText: {
    color: "#ffffff",
    fontWeight: "700",
    fontSize: 12
  },
  mentionBadge: {
    borderRadius: 999,
    backgroundColor: "#fef3c7",
    paddingHorizontal: 10,
    paddingVertical: 5
  },
  mentionBadgeText: {
    color: "#92400e",
    fontWeight: "700",
    fontSize: 12
  },
  replyBadge: {
    borderRadius: 999,
    backgroundColor: "#dbeafe",
    paddingHorizontal: 10,
    paddingVertical: 5
  },
  replyBadgeText: {
    color: "#1d4ed8",
    fontWeight: "700",
    fontSize: 12
  },
  mutedBadge: {
    borderRadius: 999,
    backgroundColor: "#e2e8f0",
    paddingHorizontal: 10,
    paddingVertical: 5
  },
  mutedBadgeText: {
    color: "#475569",
    fontWeight: "700",
    fontSize: 12
  },
  emptyState: {
    color: "#64748b",
    lineHeight: 22,
    paddingTop: 24
  },
  refreshButton: {
    marginTop: 12
  }
});
