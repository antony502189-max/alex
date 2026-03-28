import type { ChatSummary } from "../types";

export function canStartCallsFromChat(chat: ChatSummary) {
  if (chat.chatType === "SAVED") {
    return false;
  }

  if (chat.chatType === "DIRECT" && chat.peerIsBot) {
    return false;
  }

  return true;
}
