import type { ForumTopic } from "../../types";

export function buildTopicMeta(topic: ForumTopic) {
  const parts = [
    topic.generalTopic ? "General" : null,
    topic.closed ? "Closed" : "Open",
    topic.lastMessageAt ? new Date(topic.lastMessageAt).toLocaleString() : "No messages yet"
  ].filter(Boolean);
  return parts.join(" | ");
}

export function buildTopicTitle(topic: ForumTopic) {
  return `${topic.iconEmoji ? `${topic.iconEmoji} ` : ""}${topic.title}`;
}

export function buildForumTopicsSubtitle(topicCount: number) {
  return `${topicCount} topic${topicCount === 1 ? "" : "s"}`;
}
