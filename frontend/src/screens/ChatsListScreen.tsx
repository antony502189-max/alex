import React, { useEffect, useMemo, useState } from "react";
import {
  ActivityIndicator,
  FlatList,
  Pressable,
  RefreshControl,
  SafeAreaView,
  StyleSheet,
  Text,
  TextInput,
  View
} from "react-native";
import { Avatar } from "../components/Avatar";
import type { ClientFeatureFlags } from "../config/featureFlags";
import { api } from "../services/api";
import { localDatabase } from "../services/localDatabase";
import { formatPresenceStatus } from "../services/presence";
import { useAppStore } from "../store/useAppStore";
import type { ChatSummary } from "../types";

type ChatsListScreenProps = {
  featureFlags?: Partial<ClientFeatureFlags>;
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

type ChatFilter = "ALL" | "UNREAD" | "PEOPLE" | "GROUPS" | "CHANNELS" | "BOTS";

type QuickAction = {
  key: string;
  title: string;
  caption: string;
  onPress: () => void;
  tone?: "blue" | "dark" | "warm";
};

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

function matchesFilter(chat: ChatSummary, filter: ChatFilter) {
  switch (filter) {
    case "UNREAD":
      return chat.unreadCount > 0 || chat.mentionCount > 0 || chat.replyCount > 0;
    case "PEOPLE":
      return chat.chatType === "DIRECT" || chat.chatType === "SAVED";
    case "GROUPS":
      return chat.chatType === "GROUP";
    case "CHANNELS":
      return chat.chatType === "CHANNEL";
    case "BOTS":
      return chat.chatType === "DIRECT" && chat.peerIsBot;
    case "ALL":
    default:
      return true;
  }
}

function matchesSearch(chat: ChatSummary, query: string) {
  const normalized = query.trim().toLocaleLowerCase();
  if (!normalized) {
    return true;
  }
  const haystack = [
    chat.title,
    chat.about,
    chat.publicUsername,
    chat.peerDisplayName,
    chat.peerPhoneNumber,
    chat.draftText
  ]
    .filter((value): value is string => Boolean(value))
    .join(" ")
    .toLocaleLowerCase();

  return haystack.includes(normalized);
}

function formatLastActivity(value: string) {
  const timestamp = new Date(value);
  const now = new Date();
  const sameDay =
    timestamp.getFullYear() === now.getFullYear() &&
    timestamp.getMonth() === now.getMonth() &&
    timestamp.getDate() === now.getDate();

  if (sameDay) {
    return timestamp.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
  }
  return timestamp.toLocaleDateString([], { month: "short", day: "numeric" });
}

function summarizeUnread(chats: ChatSummary[]) {
  return chats.reduce((total, chat) => total + chat.unreadCount, 0);
}

export function ChatsListScreen({
  featureFlags,
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
  const [selectedFilter, setSelectedFilter] = useState<ChatFilter>("ALL");
  const [searchQuery, setSearchQuery] = useState("");
  const [error, setError] = useState<string | null>(null);
  const features = {
    stories: featureFlags?.stories ?? true,
    calls: featureFlags?.calls ?? true,
    secretChats: featureFlags?.secretChats ?? true,
    bots: featureFlags?.bots ?? true
  };

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

  const folderScopedChats = useMemo(() => {
    if (!selectedFolderId) {
      return chats;
    }
    const folder = folders.find((item) => item.folderId === selectedFolderId);
    return chats.filter((chat) => folder?.chatIds.includes(chat.chatId));
  }, [chats, folders, selectedFolderId]);

  const displayedChats = useMemo(
    () =>
      folderScopedChats.filter(
        (chat) => matchesFilter(chat, selectedFilter) && matchesSearch(chat, searchQuery)
      ),
    [folderScopedChats, searchQuery, selectedFilter]
  );

  const unreadChatsCount = chats.filter((chat) => chat.unreadCount > 0).length;
  const unreadMessagesCount = summarizeUnread(chats);
  const archivedChatsCount = chats.filter((chat) => chat.archived).length;
  const directChatsCount = chats.filter(
    (chat) => chat.chatType === "DIRECT" || chat.chatType === "SAVED"
  ).length;

  const quickActions: QuickAction[] = [
    {
      key: "saved",
      title: "Saved Messages",
      caption: "Notes, links, drafts",
      onPress: onOpenSavedMessages,
      tone: "blue"
    },
    {
      key: "contacts",
      title: "Contacts",
      caption: "People, bots, search",
      onPress: onOpenContacts
    },
    ...(features.calls
      ? [{
          key: "calls",
          title: "Calls",
          caption: "Recent voice and video",
          onPress: onOpenCalls
        }]
      : []),
    ...(features.stories
      ? [{
          key: "stories",
          title: "Stories",
          caption: "Open feed or post new",
          onPress: onOpenStories
        }]
      : []),
    {
      key: "new-chat",
      title: "New Chat",
      caption: "Start a direct dialog",
      onPress: onCreateDirect,
      tone: "dark"
    },
    {
      key: "more",
      title: "More",
      caption: "Profile and settings",
      onPress: onOpenProfile,
      tone: "warm"
    }
  ];

  const filterOptions: Array<{ id: ChatFilter; label: string }> = [
    { id: "ALL", label: "All" },
    { id: "UNREAD", label: "Unread" },
    { id: "PEOPLE", label: "People" },
    { id: "GROUPS", label: "Groups" },
    { id: "CHANNELS", label: "Channels" },
    ...(features.bots ? [{ id: "BOTS" as const, label: "Bots" }] : [])
  ];

  return (
    <SafeAreaView style={styles.screen}>
      <View style={styles.header}>
        <View style={styles.headerCopy}>
          <Text style={styles.eyebrow}>Messenger</Text>
          <Text style={styles.title}>Chats</Text>
          <Text style={styles.subtitle}>
            {session.displayName}
            {session.username ? ` | @${session.username}` : ""}
          </Text>
        </View>
        <Pressable
          disabled={signingOut}
          onPress={() => void handleLogout()}
          style={[styles.signOutButton, signingOut && styles.disabled]}
        >
          <Text style={styles.signOutText}>{signingOut ? "..." : "Sign out"}</Text>
        </Pressable>
      </View>

      <View style={styles.heroCard}>
        <View style={styles.heroTopRow}>
          <View style={styles.heroStat}>
            <Text style={styles.heroStatValue}>{unreadChatsCount}</Text>
            <Text style={styles.heroStatLabel}>active dialogs</Text>
          </View>
          <View style={styles.heroStat}>
            <Text style={styles.heroStatValue}>{unreadMessagesCount}</Text>
            <Text style={styles.heroStatLabel}>unread messages</Text>
          </View>
          <View style={styles.heroStat}>
            <Text style={styles.heroStatValue}>{directChatsCount}</Text>
            <Text style={styles.heroStatLabel}>personal chats</Text>
          </View>
        </View>
        <View style={styles.heroActionsRow}>
          <Pressable onPress={onOpenGlobalSearch} style={styles.heroPrimaryAction}>
            <Text style={styles.heroPrimaryActionText}>Global Search</Text>
          </Pressable>
          <Pressable onPress={onCreateGroup} style={styles.heroSecondaryAction}>
            <Text style={styles.heroSecondaryActionText}>New Group</Text>
          </Pressable>
          <Pressable onPress={onCreateChannel} style={styles.heroSecondaryAction}>
            <Text style={styles.heroSecondaryActionText}>New Channel</Text>
          </Pressable>
        </View>
      </View>

      <TextInput
        autoCapitalize="none"
        autoCorrect={false}
        onChangeText={setSearchQuery}
        placeholder="Search chats, usernames, phones, drafts"
        placeholderTextColor="#7c8aa5"
        style={styles.searchInput}
        value={searchQuery}
      />

      <View style={styles.filterRow}>
        {filterOptions.map((filter) => {
          const active = selectedFilter === filter.id;
          return (
            <Pressable
              key={filter.id}
              onPress={() => setSelectedFilter(filter.id)}
              style={[styles.filterChip, active && styles.filterChipActive]}
            >
              <Text style={[styles.filterChipText, active && styles.filterChipTextActive]}>
                {filter.label}
              </Text>
            </Pressable>
          );
        })}
      </View>

      <View style={styles.folderRow}>
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
          <Text style={styles.manageFoldersText}>Manage</Text>
        </Pressable>
      </View>

      <View style={styles.quickActionsGrid}>
        {quickActions.map((action) => (
          <Pressable
            key={action.key}
            onPress={action.onPress}
            style={[
              styles.quickActionCard,
              action.tone === "blue" && styles.quickActionCardBlue,
              action.tone === "dark" && styles.quickActionCardDark,
              action.tone === "warm" && styles.quickActionCardWarm
            ]}
          >
            <Text
              style={[
                styles.quickActionTitle,
                action.tone === "dark" && styles.quickActionTitleDark
              ]}
            >
              {action.title}
            </Text>
            <Text
              style={[
                styles.quickActionCaption,
                action.tone === "dark" && styles.quickActionCaptionDark
              ]}
            >
              {action.caption}
            </Text>
          </Pressable>
        ))}
      </View>

      <View style={styles.utilityRow}>
        <Pressable onPress={onOpenJoinByLink} style={styles.utilityButton}>
          <Text style={styles.utilityButtonText}>Join Link</Text>
        </Pressable>
        {features.stories ? (
          <Pressable onPress={onCreateStory} style={styles.utilityButton}>
            <Text style={styles.utilityButtonText}>New Story</Text>
          </Pressable>
        ) : null}
        {features.secretChats ? (
          <Pressable onPress={onOpenSecretChats} style={styles.utilityButton}>
            <Text style={styles.utilityButtonText}>Secret Chats</Text>
          </Pressable>
        ) : null}
        <Pressable onPress={onOpenArchived} style={styles.utilityButton}>
          <Text style={styles.utilityButtonText}>Archive {archivedChatsCount}</Text>
        </Pressable>
      </View>

      {refreshing ? <ActivityIndicator color="#2563eb" style={styles.loader} /> : null}
      {error ? <Text style={styles.errorText}>{error}</Text> : null}

      <FlatList
        contentContainerStyle={styles.listContent}
        data={displayedChats}
        keyExtractor={(item) => item.chatId}
        refreshControl={
          <RefreshControl
            onRefresh={() => void loadChats(session.token)}
            refreshing={refreshing}
            tintColor="#2563eb"
          />
        }
        ListEmptyComponent={
          <View style={styles.emptyStateCard}>
            <Text style={styles.emptyStateTitle}>No chats match the current view</Text>
            <Text style={styles.emptyStateText}>
              Try clearing the search, switching folder/filter chips, or create a new dialog.
            </Text>
          </View>
        }
        renderItem={({ item }) => (
          <Pressable onPress={() => onOpenChat(item)} style={styles.chatCard}>
            <Avatar uri={item.photoUrl} title={item.title} size={56} />
            <View style={styles.chatBody}>
              <View style={styles.chatTopRow}>
                <Text numberOfLines={1} style={styles.chatName}>
                  {item.title}
                </Text>
                <Text style={styles.chatTime}>{formatLastActivity(item.lastMessageAt)}</Text>
              </View>
              <View style={styles.chatBadgeRow}>
                {item.forumEnabled ? (
                  <View style={styles.forumBadge}>
                    <Text style={styles.forumBadgeText}>Forum</Text>
                  </View>
                ) : null}
                {item.chatType === "CHANNEL" ? (
                  <View style={styles.typeBadge}>
                    <Text style={styles.typeBadgeText}>Channel</Text>
                  </View>
                ) : null}
                {item.chatType === "GROUP" ? (
                  <View style={styles.typeBadge}>
                    <Text style={styles.typeBadgeText}>Group</Text>
                  </View>
                ) : null}
                {item.pinned ? (
                  <View style={styles.typeBadge}>
                    <Text style={styles.typeBadgeText}>Pinned</Text>
                  </View>
                ) : null}
                {item.markedUnread ? (
                  <View style={styles.mentionBadge}>
                    <Text style={styles.mentionBadgeText}>Unread</Text>
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
              <Text numberOfLines={1} style={styles.chatMeta}>
                {formatChatMeta(item)}
              </Text>
              {item.autoDeleteSeconds ? (
                <Text style={styles.chatTimer}>{formatAutoDelete(item.autoDeleteSeconds)}</Text>
              ) : null}
              {item.draftText ? (
                <Text numberOfLines={1} style={styles.chatDraft}>
                  Draft: {item.draftText}
                </Text>
              ) : item.about ? (
                <Text numberOfLines={1} style={styles.chatAbout}>
                  {item.about}
                </Text>
              ) : null}
            </View>
          </Pressable>
        )}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: "#eef4ff",
    paddingHorizontal: 18,
    paddingTop: 12
  },
  header: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: 14,
    gap: 12
  },
  headerCopy: {
    flex: 1
  },
  eyebrow: {
    color: "#2563eb",
    fontSize: 12,
    fontWeight: "700",
    letterSpacing: 1.1,
    textTransform: "uppercase"
  },
  title: {
    marginTop: 4,
    fontSize: 30,
    fontWeight: "800",
    color: "#0f172a"
  },
  subtitle: {
    marginTop: 4,
    fontSize: 14,
    color: "#5b6b88"
  },
  signOutButton: {
    borderRadius: 999,
    backgroundColor: "#dbe7ff",
    paddingHorizontal: 14,
    paddingVertical: 10
  },
  signOutText: {
    color: "#1d4ed8",
    fontWeight: "700"
  },
  disabled: {
    opacity: 0.6
  },
  heroCard: {
    borderRadius: 24,
    backgroundColor: "#0f172a",
    padding: 18,
    marginBottom: 14
  },
  heroTopRow: {
    flexDirection: "row",
    gap: 12,
    marginBottom: 16
  },
  heroStat: {
    flex: 1
  },
  heroStatValue: {
    color: "#ffffff",
    fontSize: 26,
    fontWeight: "800"
  },
  heroStatLabel: {
    marginTop: 4,
    color: "#cbd5e1",
    fontSize: 12
  },
  heroActionsRow: {
    flexDirection: "row",
    gap: 10
  },
  heroPrimaryAction: {
    flex: 1.3,
    borderRadius: 16,
    backgroundColor: "#2563eb",
    paddingVertical: 13,
    alignItems: "center"
  },
  heroPrimaryActionText: {
    color: "#ffffff",
    fontWeight: "700"
  },
  heroSecondaryAction: {
    flex: 1,
    borderRadius: 16,
    backgroundColor: "#1e293b",
    borderWidth: 1,
    borderColor: "#334155",
    paddingVertical: 13,
    alignItems: "center"
  },
  heroSecondaryActionText: {
    color: "#e2e8f0",
    fontWeight: "700"
  },
  searchInput: {
    borderRadius: 18,
    backgroundColor: "#ffffff",
    borderWidth: 1,
    borderColor: "#d7e3fb",
    paddingHorizontal: 16,
    paddingVertical: 14,
    color: "#0f172a",
    marginBottom: 12
  },
  filterRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
    marginBottom: 10
  },
  filterChip: {
    borderRadius: 999,
    backgroundColor: "#dfe9fb",
    paddingHorizontal: 12,
    paddingVertical: 8
  },
  filterChipActive: {
    backgroundColor: "#2563eb"
  },
  filterChipText: {
    color: "#1e3a8a",
    fontWeight: "700",
    fontSize: 12
  },
  filterChipTextActive: {
    color: "#ffffff"
  },
  folderRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
    marginBottom: 12
  },
  folderChip: {
    borderRadius: 999,
    backgroundColor: "#ffffff",
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderWidth: 1,
    borderColor: "#d7e3fb"
  },
  folderChipActive: {
    backgroundColor: "#0f172a",
    borderColor: "#0f172a"
  },
  folderChipText: {
    color: "#0f172a",
    fontWeight: "700",
    fontSize: 12
  },
  folderChipTextActive: {
    color: "#ffffff"
  },
  manageFoldersButton: {
    borderRadius: 999,
    backgroundColor: "#dbeafe",
    paddingHorizontal: 12,
    paddingVertical: 8
  },
  manageFoldersText: {
    color: "#1d4ed8",
    fontWeight: "700",
    fontSize: 12
  },
  quickActionsGrid: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 10,
    marginBottom: 10
  },
  quickActionCard: {
    width: "48.5%",
    borderRadius: 20,
    backgroundColor: "#ffffff",
    padding: 14,
    minHeight: 82,
    borderWidth: 1,
    borderColor: "#d7e3fb"
  },
  quickActionCardBlue: {
    backgroundColor: "#e8f0ff"
  },
  quickActionCardDark: {
    backgroundColor: "#162033",
    borderColor: "#162033"
  },
  quickActionCardWarm: {
    backgroundColor: "#fff3df"
  },
  quickActionTitle: {
    color: "#0f172a",
    fontSize: 15,
    fontWeight: "800"
  },
  quickActionTitleDark: {
    color: "#ffffff"
  },
  quickActionCaption: {
    marginTop: 6,
    color: "#64748b",
    fontSize: 12,
    lineHeight: 18
  },
  quickActionCaptionDark: {
    color: "#cbd5e1"
  },
  utilityRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
    marginBottom: 10
  },
  utilityButton: {
    borderRadius: 14,
    backgroundColor: "#dfe9fb",
    paddingHorizontal: 12,
    paddingVertical: 10
  },
  utilityButtonText: {
    color: "#1d4ed8",
    fontWeight: "700",
    fontSize: 12
  },
  loader: {
    marginVertical: 8
  },
  errorText: {
    color: "#b91c1c",
    fontSize: 14,
    marginBottom: 8
  },
  listContent: {
    gap: 10,
    paddingBottom: 20
  },
  emptyStateCard: {
    borderRadius: 20,
    backgroundColor: "#ffffff",
    padding: 18,
    marginTop: 8,
    borderWidth: 1,
    borderColor: "#d7e3fb"
  },
  emptyStateTitle: {
    color: "#0f172a",
    fontSize: 16,
    fontWeight: "800"
  },
  emptyStateText: {
    marginTop: 8,
    color: "#64748b",
    lineHeight: 21
  },
  chatCard: {
    borderRadius: 22,
    backgroundColor: "#ffffff",
    padding: 14,
    flexDirection: "row",
    gap: 14,
    alignItems: "center",
    borderWidth: 1,
    borderColor: "#d7e3fb"
  },
  chatBody: {
    flex: 1,
    gap: 5
  },
  chatTopRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10
  },
  chatName: {
    flex: 1,
    color: "#0f172a",
    fontSize: 17,
    fontWeight: "700"
  },
  chatTime: {
    color: "#64748b",
    fontSize: 12,
    fontWeight: "600"
  },
  chatBadgeRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 6
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
  typeBadge: {
    borderRadius: 999,
    backgroundColor: "#e2e8f0",
    paddingHorizontal: 8,
    paddingVertical: 4
  },
  typeBadgeText: {
    color: "#475569",
    fontSize: 11,
    fontWeight: "700"
  },
  unreadBadge: {
    minWidth: 28,
    borderRadius: 999,
    backgroundColor: "#16a34a",
    paddingHorizontal: 8,
    paddingVertical: 4,
    alignItems: "center"
  },
  unreadBadgeText: {
    color: "#ffffff",
    fontWeight: "800",
    fontSize: 12
  },
  mentionBadge: {
    borderRadius: 999,
    backgroundColor: "#fef3c7",
    paddingHorizontal: 8,
    paddingVertical: 4
  },
  mentionBadgeText: {
    color: "#92400e",
    fontWeight: "800",
    fontSize: 12
  },
  replyBadge: {
    borderRadius: 999,
    backgroundColor: "#dbeafe",
    paddingHorizontal: 8,
    paddingVertical: 4
  },
  replyBadgeText: {
    color: "#1d4ed8",
    fontWeight: "800",
    fontSize: 12
  },
  mutedBadge: {
    borderRadius: 999,
    backgroundColor: "#f1f5f9",
    paddingHorizontal: 8,
    paddingVertical: 4
  },
  mutedBadgeText: {
    color: "#64748b",
    fontWeight: "700",
    fontSize: 11
  },
  chatMeta: {
    color: "#64748b",
    fontSize: 13
  },
  chatTimer: {
    fontSize: 12,
    color: "#0f766e",
    fontWeight: "700"
  },
  chatDraft: {
    color: "#c2410c",
    fontSize: 13,
    fontWeight: "700"
  },
  chatAbout: {
    color: "#475569",
    fontSize: 13
  }
});
