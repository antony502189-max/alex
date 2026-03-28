import { formatChatAudienceCount } from "../../services/chatAudience";
import type { ChatSummary } from "../../types";

export function formatArchivedChatAutoDelete(seconds: number | null) {
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

export function buildArchivedChatMeta(chat: ChatSummary) {
  if (chat.chatType === "DIRECT") {
    return chat.peerPhoneNumber ?? "phone-hidden";
  }

  if (chat.chatType === "SAVED") {
    return "private notes";
  }

  const parts = [
    chat.publicUsername ? `@${chat.publicUsername}` : null,
    formatChatAudienceCount(chat.chatType, chat.memberCount),
    chat.forumEnabled ? `${chat.topicCount} topic${chat.topicCount === 1 ? "" : "s"}` : null
  ].filter(Boolean);

  return parts.join(" | ");
}

export function buildArchivedChatPreview(chat: ChatSummary) {
  if (chat.draftText) {
    return `Draft: ${chat.draftText}`;
  }

  return chat.about;
}
