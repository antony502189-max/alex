import type {
  SecretChatAttachment,
  SecretChatMessage,
  SecretChatSummary
} from "../../types";

export type ResolvedSecretChatMessage = {
  raw: SecretChatMessage;
  text: string | null;
  attachments: SecretChatAttachment[];
  failed: boolean;
};

export function mergeSecretChatMessages(
  current: SecretChatMessage[],
  incoming: SecretChatMessage[]
) {
  const map = new Map<string, SecretChatMessage>();
  for (const message of [...current, ...incoming]) {
    map.set(message.secretMessageId, message);
  }
  return [...map.values()].sort((left, right) => left.createdAt.localeCompare(right.createdAt));
}

export function filterVisibleSecretMessages(messages: SecretChatMessage[], nowMs = Date.now()) {
  return messages.filter((message) => !message.expiresAt || new Date(message.expiresAt).getTime() > nowMs);
}

export function formatSecretFileSize(fileSizeBytes: number) {
  if (fileSizeBytes >= 1024 * 1024) {
    return `${(fileSizeBytes / (1024 * 1024)).toFixed(1)} MB`;
  }
  if (fileSizeBytes >= 1024) {
    return `${Math.round(fileSizeBytes / 1024)} KB`;
  }
  return `${fileSizeBytes} B`;
}

export function formatSecretDuration(durationMs: number | null | undefined) {
  const totalSeconds = Math.max(0, Math.round((durationMs ?? 0) / 1000));
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${seconds.toString().padStart(2, "0")}`;
}

export function inferSecretAttachmentKind(
  requestedKind: "FILE" | "IMAGE",
  assetName: string | null | undefined,
  mimeType: string | null | undefined
): "FILE" | "IMAGE" | "VIDEO" {
  if (requestedKind === "IMAGE") {
    return "IMAGE";
  }

  const normalizedMimeType = mimeType?.trim().toLowerCase() ?? "";
  if (normalizedMimeType.startsWith("video/")) {
    return "VIDEO";
  }

  const normalizedName = assetName?.trim().toLowerCase() ?? "";
  if (/\.(mp4|mov|m4v|webm|mkv|avi)$/i.test(normalizedName)) {
    return "VIDEO";
  }

  return "FILE";
}

export function buildSecretChatStatusText(secretChat: SecretChatSummary) {
  if (secretChat.status === "PENDING") {
    return secretChat.direction === "OUTGOING"
      ? "Waiting for peer device to accept"
      : "Incoming request";
  }
  if (secretChat.status === "ACTIVE") {
    return secretChat.autoDeleteSeconds
      ? `Active - auto-delete ${secretChat.autoDeleteSeconds}s`
      : "Active";
  }
  if (secretChat.status === "DECLINED") {
    return "Declined";
  }
  return "Closed";
}

export function buildResolvedSecretMessageMeta(
  message: ResolvedSecretChatMessage,
  currentUserId: string
) {
  const isMine = message.raw.senderUserId === currentUserId;
  const metaParts = [new Date(message.raw.createdAt).toLocaleString()];
  if (isMine && message.raw.readAt) {
    metaParts.push(`read ${new Date(message.raw.readAt).toLocaleTimeString()}`);
  }
  if (message.raw.expiresAt) {
    metaParts.push(`expires ${new Date(message.raw.expiresAt).toLocaleTimeString()}`);
  }
  if (message.failed) {
    metaParts.push("undecryptable");
  }
  return metaParts.join(" - ");
}

export function buildSecretAttachmentTitle(attachment: SecretChatAttachment) {
  if (attachment.kind === "VOICE") {
    return "Secret voice note";
  }
  if (attachment.kind === "VIDEO") {
    return "Secret video note";
  }
  if (attachment.kind === "IMAGE") {
    return "Photo";
  }
  return attachment.originalFileName;
}

export function buildSecretAttachmentMeta(attachment: SecretChatAttachment) {
  if (attachment.kind === "VOICE") {
    return `${formatSecretDuration(attachment.durationMs)} - ${formatSecretFileSize(attachment.fileSizeBytes)}`;
  }
  if (attachment.kind === "VIDEO") {
    return attachment.durationMs
      ? `${formatSecretDuration(attachment.durationMs)} - ${formatSecretFileSize(attachment.fileSizeBytes)}`
      : formatSecretFileSize(attachment.fileSizeBytes);
  }
  return `${attachment.contentType} - ${formatSecretFileSize(attachment.fileSizeBytes)}`;
}

export function buildSecretAttachmentActionLabel(params: {
  attachment: SecretChatAttachment;
  imageVisible: boolean;
  opening: boolean;
}) {
  const { attachment, imageVisible, opening } = params;
  if (opening) {
    return "Opening...";
  }
  if (attachment.kind === "IMAGE") {
    return imageVisible ? "View photo" : "Decrypt photo";
  }
  if (attachment.kind === "VIDEO") {
    return "Open video";
  }
  if (attachment.kind === "VOICE") {
    return "Play";
  }
  return "Decrypt only";
}

export function buildSecretChatDisabledComposerNotice(secretChat: SecretChatSummary) {
  return secretChat.direction === "OUTGOING"
    ? "Messages are disabled until the peer accepts this device-bound secret chat."
    : "Accept this secret chat from the previous screen to bind it to this device.";
}
