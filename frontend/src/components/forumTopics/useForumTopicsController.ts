import { useEffect, useState } from "react";
import { api } from "../../services/api";
import { localDatabase } from "../../services/localDatabase";
import type { ChatSummary, ForumTopic } from "../../types";

type UseForumTopicsControllerParams = {
  chat: ChatSummary;
  currentUserId: string;
  onRefreshChats?: () => Promise<void> | void;
  token: string;
};

export function useForumTopicsController({
  chat,
  currentUserId,
  onRefreshChats,
  token
}: UseForumTopicsControllerParams) {
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

  return {
    error,
    handleCreateTopic,
    handleToggleTopicClosed,
    iconEmoji,
    loading,
    saving,
    setIconEmoji,
    setTitle,
    title,
    topics
  };
}

export type ForumTopicsScreenController = ReturnType<typeof useForumTopicsController>;
