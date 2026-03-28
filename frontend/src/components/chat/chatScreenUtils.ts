import type { MessageAttachment, ScheduledMessage } from "../../types";

export type ActiveInlineBotQuery = {
  botUsername: string;
  query: string;
};

export function mergeScheduledMessages(messages: ScheduledMessage[]) {
  const map = new Map<string, ScheduledMessage>();

  function buildKey(message: ScheduledMessage) {
    return message.clientMessageId
      ? `client:${message.clientMessageId}`
      : `scheduled:${message.scheduledMessageId}`;
  }

  function preferMessage(left: ScheduledMessage, right: ScheduledMessage) {
    if (left.status === "QUEUED" && right.status !== "QUEUED") {
      return right;
    }
    if (right.status === "QUEUED" && left.status !== "QUEUED") {
      return left;
    }
    return right.createdAt.localeCompare(left.createdAt) >= 0 ? right : left;
  }

  for (const message of messages) {
    const key = buildKey(message);
    const existing = map.get(key);
    map.set(key, existing ? preferMessage(existing, message) : message);
  }

  return [...map.values()].sort((left, right) =>
    left.scheduledAt.localeCompare(right.scheduledAt)
  );
}

export function parseInlineBotQuery(value: string): ActiveInlineBotQuery | null {
  const trimmed = value.trimStart();
  const match = trimmed.match(/^@([A-Za-z0-9_]{3,64})(?:\s+(.*))?$/s);
  if (!match) {
    return null;
  }
  return {
    botUsername: match[1].toLowerCase(),
    query: match[2]?.trim() ?? ""
  };
}

export function getImagePreviewHeight(attachment: MessageAttachment) {
  if (!attachment.width || !attachment.height || attachment.width <= 0) {
    return 220;
  }
  const scaled = Math.round((220 * attachment.height) / attachment.width);
  return Math.max(120, Math.min(360, scaled));
}

export function formatDuration(durationMs: number | null | undefined) {
  const totalSeconds = Math.max(0, Math.round((durationMs ?? 0) / 1000));
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${seconds.toString().padStart(2, "0")}`;
}

export function formatCooldown(totalMs: number) {
  const totalSeconds = Math.max(1, Math.ceil(totalMs / 1000));
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return minutes > 0 ? `${minutes}m ${seconds}s` : `${seconds}s`;
}
