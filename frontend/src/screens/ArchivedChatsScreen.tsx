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
import type { ChatSummary } from "../types";

type ArchivedChatsScreenProps = {
  token: string;
  onClose: () => void;
  onOpenChat: (chat: ChatSummary) => void;
};

export function ArchivedChatsScreen({
  token,
  onClose,
  onOpenChat
}: ArchivedChatsScreenProps) {
  const [chats, setChats] = useState<ChatSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoading(true);
      setError(null);
      try {
        const nextChats = await api.getArchivedChats(token);
        if (!cancelled) {
          setChats(nextChats);
        }
      } catch (loadError) {
        if (!cancelled) {
          setError(loadError instanceof Error ? loadError.message : "Unable to load archived chats");
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }
    void load();
    return () => {
      cancelled = true;
    };
  }, [token]);

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
      return chat.peerPhoneNumber ?? "phone-hidden";
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

  return (
    <SafeAreaView style={styles.screen}>
      <View style={styles.header}>
        <Pressable onPress={onClose} style={styles.secondaryButton}>
          <Text style={styles.secondaryButtonText}>Back</Text>
        </Pressable>
        <Text style={styles.title}>Archived</Text>
      </View>

      {loading ? <ActivityIndicator color="#0f172a" style={styles.loader} /> : null}
      {error ? <Text style={styles.errorText}>{error}</Text> : null}

      <FlatList
        contentContainerStyle={styles.listContent}
        data={chats}
        keyExtractor={(item) => item.chatId}
        ListEmptyComponent={<Text style={styles.emptyState}>No archived chats.</Text>}
        renderItem={({ item }) => (
          <Pressable onPress={() => onOpenChat(item)} style={styles.card}>
            <Avatar uri={item.photoUrl} title={item.title} size={52} />
            <View style={styles.cardBody}>
              <View style={styles.cardTopRow}>
                <Text style={styles.cardTitle}>{item.title}</Text>
                {item.forumEnabled ? (
                  <View style={styles.forumBadge}>
                    <Text style={styles.forumBadgeText}>Forum</Text>
                  </View>
                ) : null}
              </View>
              <Text style={styles.cardMeta}>{formatChatMeta(item)}</Text>
              {item.autoDeleteSeconds ? (
                <Text style={styles.cardTimer}>{formatAutoDelete(item.autoDeleteSeconds)}</Text>
              ) : null}
              {item.draftText ? (
                <Text numberOfLines={1} style={styles.cardDraft}>
                  Draft: {item.draftText}
                </Text>
              ) : item.about ? (
                <Text numberOfLines={1} style={styles.cardMeta}>
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
  screen: { flex: 1, backgroundColor: "#f8fafc", padding: 20 },
  header: { flexDirection: "row", alignItems: "center", gap: 12, marginBottom: 16 },
  title: { fontSize: 24, fontWeight: "700", color: "#0f172a" },
  secondaryButton: {
    borderRadius: 12,
    backgroundColor: "#e2e8f0",
    paddingHorizontal: 14,
    paddingVertical: 10
  },
  secondaryButtonText: { color: "#0f172a", fontWeight: "600" },
  loader: { marginBottom: 12 },
  errorText: { color: "#b91c1c", marginBottom: 12 },
  listContent: { gap: 12, paddingBottom: 20 },
  card: {
    borderRadius: 18,
    backgroundColor: "#ffffff",
    padding: 16,
    gap: 14,
    flexDirection: "row",
    alignItems: "center"
  },
  cardBody: { flex: 1, gap: 4 },
  cardTopRow: {
    flexDirection: "row",
    alignItems: "center",
    flexWrap: "wrap",
    gap: 8
  },
  cardTitle: { fontSize: 18, fontWeight: "600", color: "#0f172a" },
  cardMeta: { color: "#64748b" },
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
  cardTimer: { color: "#0f766e", fontWeight: "700" },
  cardDraft: { color: "#b45309", fontWeight: "600" },
  emptyState: { color: "#64748b", paddingTop: 24 }
});
