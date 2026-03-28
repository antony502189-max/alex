import type { CallSession, ChatSummary } from "../types";

export function isLiveCall(call: CallSession | null, currentUserId: string) {
  if (!call || !["RINGING", "ACTIVE"].includes(call.status)) {
    return false;
  }

  const myParticipant = call.participants.find((participant) => participant.userId === currentUserId);
  return !!myParticipant && !["LEFT", "DECLINED", "MISSED"].includes(myParticipant.state);
}

export function pickPreferredCall(calls: CallSession[], currentUserId: string) {
  return [...calls]
    .filter((call) => isLiveCall(call, currentUserId))
    .sort((left, right) =>
      (right.answeredAt ?? right.startedAt).localeCompare(left.answeredAt ?? left.startedAt)
    )[0] ?? null;
}

export function deriveCallTitle(call: CallSession, chats: ChatSummary[], currentUserId: string) {
  const chat = chats.find((item) => item.chatId === call.chatId);
  if (chat) {
    return chat.title;
  }

  const others = call.participants.filter((participant) => participant.userId !== currentUserId);
  if (others.length === 0) {
    return "Call";
  }
  if (others.length === 1) {
    return others[0].displayName;
  }
  return others.map((participant) => participant.displayName).join(", ");
}

export function deriveCallPhoto(call: CallSession, chats: ChatSummary[], currentUserId: string) {
  const chat = chats.find((item) => item.chatId === call.chatId);
  if (chat?.photoUrl) {
    return chat.photoUrl;
  }

  return call.participants.find((participant) => participant.userId !== currentUserId)?.photoUrl ?? null;
}
