const QUEUED_MESSAGE_PREFIX = "queued:";
const QUEUED_SCHEDULED_MESSAGE_PREFIX = "queued-scheduled:";

export function generateClientMessageId() {
  if (typeof globalThis.crypto?.randomUUID === "function") {
    return globalThis.crypto.randomUUID();
  }

  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (character) => {
    const random = Math.floor(Math.random() * 16);
    const value = character === "x" ? random : (random & 0x3) | 0x8;
    return value.toString(16);
  });
}

export function toQueuedMessageId(clientMessageId: string) {
  return `${QUEUED_MESSAGE_PREFIX}${clientMessageId}`;
}

export function toQueuedScheduledMessageId(clientMessageId: string) {
  return `${QUEUED_SCHEDULED_MESSAGE_PREFIX}${clientMessageId}`;
}

export function fromQueuedScheduledMessageId(scheduledMessageId: string) {
  return scheduledMessageId.startsWith(QUEUED_SCHEDULED_MESSAGE_PREFIX)
    ? scheduledMessageId.slice(QUEUED_SCHEDULED_MESSAGE_PREFIX.length)
    : null;
}
