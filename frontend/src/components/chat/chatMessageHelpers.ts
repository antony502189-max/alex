import type {
  ChatMember,
  ChatMessage,
  MessageAttachment,
  MessageContactCard,
  MessageLiveLocation,
  MessageLocation,
  ScheduledMessage
} from "../../types";

type SenderLookupMember = Pick<ChatMember, "displayName" | "lastReadAt" | "userId">;

export function formatLocationSummary(location: MessageLocation | null) {
  if (!location) {
    return "Location";
  }

  const title = location.title?.trim();
  const address = location.address?.trim();
  if (title && address) {
    return `${title} - ${address}`;
  }
  if (title) {
    return title;
  }
  if (address) {
    return address;
  }
  return `${location.latitude.toFixed(5)}, ${location.longitude.toFixed(5)}`;
}

export function formatLiveLocationSummary(location: MessageLiveLocation | null | undefined) {
  if (!location) {
    return "Live location";
  }

  const summary = formatLocationSummary(location);
  if (location.active === false || location.stoppedAt) {
    return `${summary} - stopped`;
  }
  if (location.livePeriodSeconds) {
    return `${summary} - live for ${Math.round(location.livePeriodSeconds / 60)}m`;
  }
  return `${summary} - live`;
}

export function formatContactName(contactCard: MessageContactCard | null) {
  if (!contactCard) {
    return "Contact";
  }

  const fullName = [contactCard.firstName, contactCard.lastName]
    .filter((part) => !!part)
    .join(" ")
    .trim();
  if (fullName) {
    return fullName;
  }
  return contactCard.phoneNumber ?? "Contact";
}

export function describeMessage(
  message: ChatMessage | ScheduledMessage,
  attachmentTitle: (attachment: MessageAttachment) => string
) {
  if (message.serviceMessage?.text) {
    return message.serviceMessage.text;
  }
  if (message.messageType === "LOCATION") {
    return formatLocationSummary(message.location);
  }
  if (message.messageType === "LIVE_LOCATION") {
    return formatLiveLocationSummary(message.liveLocation);
  }
  if (message.messageType === "CONTACT_CARD") {
    return formatContactName(message.contactCard);
  }
  if (message.text) {
    return message.text;
  }
  if (message.attachments.length > 0) {
    return message.attachments.length > 1
      ? "Attachment album"
      : attachmentTitle(message.attachments[0]);
  }
  if ("stickerId" in message && message.stickerId) {
    return "Sticker";
  }
  return "Message";
}

export function resolveDisplaySenderName(
  message: ChatMessage | null | undefined,
  currentUserId: string,
  members: SenderLookupMember[]
) {
  if (!message) {
    return null;
  }
  if (message.displaySenderName) {
    return message.displaySenderName;
  }
  if (message.senderId === currentUserId) {
    return "You";
  }
  return members.find((member) => member.userId === message.senderId)?.displayName ?? null;
}

export function seenCount(
  message: ChatMessage,
  currentUserId: string,
  members: SenderLookupMember[]
) {
  if (message.senderId !== currentUserId) {
    return 0;
  }

  return members.filter(
    (member) =>
      member.userId !== currentUserId &&
      member.lastReadAt &&
      new Date(member.lastReadAt).getTime() >= new Date(message.createdAt).getTime()
  ).length;
}

export function renderMessageMeta(
  message: ChatMessage,
  currentUserId: string,
  members: SenderLookupMember[]
) {
  const parts: string[] = [new Date(message.createdAt).toLocaleTimeString()];
  if (message.forwardedFromMessageId) {
    parts.push("forwarded");
  }
  if (message.silent) {
    parts.push("silent");
  }
  if (message.anonymousSender) {
    parts.push("anonymous admin");
  }
  if (message.senderId === currentUserId) {
    parts.push(message.deliveryStatus.toLowerCase());
  }
  if (message.editedAt) {
    parts.push("edited");
  }
  if (message.expiresAt && !message.deletedAt) {
    parts.push(`expires ${new Date(message.expiresAt).toLocaleTimeString()}`);
  }
  const readCount = seenCount(message, currentUserId, members);
  if (readCount > 0) {
    parts.push(`seen by ${readCount}`);
  }
  return parts.join(" - ");
}

export function formatFileSize(fileSizeBytes: number) {
  if (fileSizeBytes < 1024) {
    return `${fileSizeBytes} B`;
  }
  if (fileSizeBytes < 1024 * 1024) {
    return `${(fileSizeBytes / 1024).toFixed(1)} KB`;
  }
  return `${(fileSizeBytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function formatAutoDelete(seconds: number | null) {
  if (!seconds) {
    return null;
  }
  if (seconds < 60) {
    return `auto-delete ${seconds}s`;
  }
  if (seconds < 3600) {
    return `auto-delete ${Math.round(seconds / 60)}m`;
  }
  if (seconds < 86400) {
    return `auto-delete ${Math.round(seconds / 3600)}h`;
  }
  return `auto-delete ${Math.round(seconds / 86400)}d`;
}
