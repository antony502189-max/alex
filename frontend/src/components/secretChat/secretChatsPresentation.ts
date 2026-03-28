import type { SecretChatSummary } from "../../types";

export function sortSecretChats(secretChats: SecretChatSummary[]) {
  return [...secretChats].sort((left, right) =>
    (right.lastMessageAt ?? right.acceptedAt ?? right.createdAt).localeCompare(
      left.lastMessageAt ?? left.acceptedAt ?? left.createdAt
    )
  );
}

export function upsertSecretChat(current: SecretChatSummary[], next: SecretChatSummary) {
  return sortSecretChats([
    ...current.filter((item) => item.secretChatId !== next.secretChatId),
    next
  ]);
}

export function removeSecretChat(current: SecretChatSummary[], secretChatId: string) {
  return current.filter((item) => item.secretChatId !== secretChatId);
}

export function formatSecretChatListState(secretChat: SecretChatSummary) {
  if (secretChat.status === "PENDING") {
    return secretChat.direction === "OUTGOING"
      ? "Waiting for peer device to accept"
      : "Incoming request";
  }
  if (secretChat.status === "ACTIVE") {
    return secretChat.autoDeleteSeconds
      ? `Active - TTL ${secretChat.autoDeleteSeconds}s`
      : "Active";
  }
  if (secretChat.status === "DECLINED") {
    return "Declined";
  }
  return "Closed";
}

export function buildSecretChatPeerMeta(secretChat: SecretChatSummary) {
  return `${secretChat.peerDeviceName ?? "Device not bound yet"} - ${secretChat.direction.toLowerCase()}`;
}
