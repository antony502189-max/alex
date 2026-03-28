import type { ChatSummary } from "../types";

export function formatChatAudienceCount(
  chatType: ChatSummary["chatType"],
  count: number
) {
  const label = chatType === "CHANNEL"
    ? count === 1 ? "subscriber" : "subscribers"
    : count === 1 ? "member" : "members";

  return `${count} ${label}`;
}
