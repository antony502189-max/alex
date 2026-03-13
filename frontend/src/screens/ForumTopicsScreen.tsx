import React, { useEffect, useState } from "react";
import {
  ActivityIndicator,
  FlatList,
  Pressable,
  SafeAreaView,
  StyleSheet,
  Text,
  TextInput,
  View
} from "react-native";
import { Avatar } from "../components/Avatar";
import { api } from "../services/api";
import { localDatabase } from "../services/localDatabase";
import type { ChatSummary, ForumTopic } from "../types";

type ForumTopicsScreenProps = {
  chat: ChatSummary;
  currentUserId: string;
  token: string;
  onBack: () => void;
  onOpenTopic: (topic: ForumTopic) => void;
  onRefreshChats?: () => Promise<void> | void;
};

function buildTopicMeta(topic: ForumTopic) {
  const parts = [
    topic.generalTopic ? "General" : null,
    topic.closed ? "Closed" : "Open",
    topic.lastMessageAt ? new Date(topic.lastMessageAt).toLocaleString() : "No messages yet"
  ].filter(Boolean);
  return parts.join(" | ");
}

export function ForumTopicsScreen({
  chat,
  currentUserId,
  token,
  onBack,
  onOpenTopic,
  onRefreshChats
}: ForumTopicsScreenProps) {
  const [topics, setTopics] = useState<ForumTopic[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [title, setTitle] = useState("");
  const [iconEmoji, setIconEmoji] = useState("");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    let cachedTopics: ForumTopic[] = [];

    async function loadTopics() {
      setLoading(true);
      setError(null);
      try {
        cachedTopics = await localDatabase.getForumTopics(currentUserId, chat.chatId);
        if (!cancelled && cachedTopics.length > 0) {
          setTopics(cachedTopics);
        }

        const nextTopics = await api.getForumTopics(token, chat.chatId);
        if (!cancelled) {
          setTopics(nextTopics);
          await localDatabase.replaceForumTopics(currentUserId, chat.chatId, nextTopics);
        }
      } catch (loadError) {
        if (!cancelled) {
          setError(
            cachedTopics.length > 0
              ? "Offline mode. Showing cached topics."
              : loadError instanceof Error
                ? loadError.message
                : "Unable to load topics"
          );
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    void loadTopics();

    return () => {
      cancelled = true;
    };
  }, [chat.chatId, currentUserId, token]);

  async function handleCreateTopic() {
    if (!title.trim() || saving) {
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const topic = await api.createForumTopic(token, chat.chatId, {
        title: title.trim(),
        iconEmoji: iconEmoji.trim() || undefined
      });
      const nextTopics = [topic, ...topics.filter((item) => item.topicId !== topic.topicId)];
      setTopics(nextTopics);
      await localDatabase.upsertForumTopics(currentUserId, [topic]);
      await onRefreshChats?.();
      setTitle("");
      setIconEmoji("");
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : "Unable to create topic");
    } finally {
      setSaving(false);
    }
  }

  async function handleToggleTopicClosed(topic: ForumTopic) {
    if (saving || topic.generalTopic) {
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const updated = await api.updateForumTopic(token, chat.chatId, topic.topicId, {
        closed: !topic.closed
      });
      const nextTopics = topics.map((item) =>
        item.topicId === updated.topicId ? updated : item
      );
      setTopics(nextTopics);
      await localDatabase.upsertForumTopics(currentUserId, [updated]);
    } catch (updateError) {
      setError(updateError instanceof Error ? updateError.message : "Unable to update topic");
    } finally {
      setSaving(false);
    }
  }

  return (
    <SafeAreaView style={styles.screen}>
      <View style={styles.header}>
        <Pressable onPress={onBack} style={styles.secondaryButton}>
          <Text style={styles.secondaryButtonText}>Back</Text>
        </Pressable>
        <Avatar size={56} title={chat.title} uri={chat.photoUrl} />
        <View style={styles.headerText}>
          <Text style={styles.title}>{chat.title}</Text>
          <Text style={styles.subtitle}>
            {topics.length} topic{topics.length === 1 ? "" : "s"}
          </Text>
        </View>
      </View>

      <View style={styles.composerCard}>
        <Text style={styles.sectionTitle}>New topic</Text>
        <View style={styles.row}>
          <TextInput
            onChangeText={setTitle}
            placeholder="Topic title"
            style={[styles.input, styles.titleInput]}
            value={title}
          />
          <TextInput
            autoCapitalize="none"
            onChangeText={setIconEmoji}
            placeholder="Emoji"
            style={[styles.input, styles.emojiInput]}
            value={iconEmoji}
          />
        </View>
        <Pressable
          disabled={saving || !title.trim()}
          onPress={() => void handleCreateTopic()}
          style={[styles.primaryButton, (saving || !title.trim()) && styles.disabled]}
        >
          <Text style={styles.primaryButtonText}>{saving ? "..." : "Create topic"}</Text>
        </Pressable>
      </View>

      {loading ? <ActivityIndicator color="#0f172a" style={styles.loader} /> : null}
      {error ? <Text style={styles.errorText}>{error}</Text> : null}

      <FlatList
        contentContainerStyle={styles.listContent}
        data={topics}
        keyExtractor={(item) => item.topicId}
        renderItem={({ item }) => (
          <Pressable onPress={() => onOpenTopic(item)} style={styles.card}>
            <View style={styles.cardBody}>
              <Text style={styles.cardTitle}>
                {item.iconEmoji ? `${item.iconEmoji} ` : ""}
                {item.title}
              </Text>
              <Text style={styles.cardMeta}>{buildTopicMeta(item)}</Text>
            </View>
            {!item.generalTopic ? (
              <Pressable
                disabled={saving}
                onPress={(event) => {
                  event.stopPropagation();
                  void handleToggleTopicClosed(item);
                }}
                style={[styles.secondaryButton, saving && styles.disabled]}
              >
                <Text style={styles.secondaryButtonText}>{item.closed ? "Reopen" : "Close"}</Text>
              </Pressable>
            ) : null}
          </Pressable>
        )}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: "#f8fafc" },
  header: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    paddingHorizontal: 20,
    paddingTop: 16,
    paddingBottom: 12
  },
  headerText: { flex: 1 },
  title: { fontSize: 24, fontWeight: "700", color: "#0f172a" },
  subtitle: { color: "#64748b", marginTop: 2 },
  composerCard: {
    marginHorizontal: 16,
    marginBottom: 12,
    borderRadius: 16,
    backgroundColor: "#ffffff",
    padding: 14,
    gap: 10
  },
  sectionTitle: { color: "#0f172a", fontWeight: "700" },
  row: { flexDirection: "row", gap: 8 },
  input: {
    borderWidth: 1,
    borderColor: "#cbd5e1",
    borderRadius: 14,
    backgroundColor: "#ffffff",
    paddingHorizontal: 14,
    paddingVertical: 12
  },
  titleInput: { flex: 1 },
  emojiInput: { width: 88 },
  primaryButton: {
    borderRadius: 14,
    backgroundColor: "#0f172a",
    paddingHorizontal: 16,
    paddingVertical: 14,
    alignItems: "center"
  },
  primaryButtonText: { color: "#ffffff", fontWeight: "700" },
  secondaryButton: {
    borderRadius: 12,
    backgroundColor: "#e2e8f0",
    paddingHorizontal: 12,
    paddingVertical: 10
  },
  secondaryButtonText: { color: "#0f172a", fontWeight: "600" },
  disabled: { opacity: 0.6 },
  loader: { marginBottom: 12 },
  errorText: { color: "#b91c1c", paddingHorizontal: 16, marginBottom: 12 },
  listContent: { gap: 10, paddingHorizontal: 16, paddingBottom: 24 },
  card: {
    borderRadius: 16,
    backgroundColor: "#ffffff",
    paddingHorizontal: 14,
    paddingVertical: 12,
    flexDirection: "row",
    alignItems: "center",
    gap: 12
  },
  cardBody: { flex: 1 },
  cardTitle: { color: "#0f172a", fontWeight: "700", fontSize: 16 },
  cardMeta: { color: "#64748b", marginTop: 4 }
});
