import { formatChatAudienceCount } from "../../services/chatAudience";
import type { ChatFolder, ChatSummary } from "../../types";

export function sortFoldersByPosition(folders: ChatFolder[]) {
  return [...folders].sort((left, right) => left.position - right.position);
}

export function formatFolderChatMeta(chat: ChatSummary) {
  if (chat.chatType === "DIRECT") {
    return chat.peerPhoneNumber ?? "phone-hidden";
  }
  if (chat.chatType === "SAVED") {
    return "private notes";
  }

  const parts = [
    formatChatAudienceCount(chat.chatType, chat.memberCount),
    chat.forumEnabled ? `${chat.topicCount} topic${chat.topicCount === 1 ? "" : "s"}` : null
  ].filter(Boolean);

  return parts.join(" - ");
}

export function buildFolderSaveLabel(saving: boolean) {
  return saving ? "Saving..." : "Save folder";
}

export function buildFolderEmptyState() {
  return "Select chats to group them into a custom folder.";
}
