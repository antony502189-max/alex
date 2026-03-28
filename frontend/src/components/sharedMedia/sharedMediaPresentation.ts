import type {
  ChatMessage,
  MessageAttachment,
  SharedMediaBuckets
} from "../../types";

const URL_PATTERN =
  /((?:https?|alex|tg|telegram):\/\/[^\s]+|(?:www\.)?(?:t\.me|telegram\.me)\/[^\s]+)/gi;

function sortByNewest<T extends { createdAt: string }>(items: T[]) {
  return [...items].sort((left, right) => right.createdAt.localeCompare(left.createdAt));
}

export function isMediaAttachment(attachment: MessageAttachment) {
  return (
    attachment.kind === "IMAGE" ||
    attachment.kind === "GIF" ||
    attachment.kind === "VIDEO" ||
    attachment.kind === "VIDEO_NOTE" ||
    attachment.contentType.startsWith("image/") ||
    attachment.contentType.startsWith("video/")
  );
}

export function isAudioAttachment(attachment: MessageAttachment) {
  return (
    attachment.kind === "VOICE" ||
    attachment.kind === "AUDIO" ||
    attachment.contentType.startsWith("audio/")
  );
}

export function buildSharedMediaBuckets(
  chatId: string,
  messages: ChatMessage[]
): SharedMediaBuckets {
  const media: SharedMediaBuckets["media"] = [];
  const files: SharedMediaBuckets["files"] = [];
  const links: SharedMediaBuckets["links"] = [];

  for (const message of messages) {
    if (message.deletedAt) {
      continue;
    }

    for (const attachment of message.attachments) {
      const entry = {
        chatId,
        messageId: message.messageId,
        createdAt: message.createdAt,
        senderDisplayName: message.displaySenderName,
        caption: message.caption ?? message.text ?? null,
        attachment
      };

      if (isMediaAttachment(attachment)) {
        media.push(entry);
      } else {
        files.push(entry);
      }
    }

    const textToScan = [message.text, message.caption].filter(Boolean).join("\n");
    if (!textToScan) {
      continue;
    }

    const matches = [...textToScan.matchAll(URL_PATTERN)].map((match) => match[0]);
    matches.forEach((url, index) => {
      links.push({
        linkId: `${message.messageId}:${index}:${url}`,
        chatId,
        messageId: message.messageId,
        createdAt: message.createdAt,
        url,
        label: message.text || message.caption || null
      });
    });
  }

  return {
    chatId,
    media: sortByNewest(media),
    files: sortByNewest(files),
    links: sortByNewest(links),
    loadedAt: new Date().toISOString()
  };
}

export function formatFileSize(fileSizeBytes: number) {
  if (fileSizeBytes < 1024) {
    return `${fileSizeBytes} B`;
  }
  if (fileSizeBytes < 1024 * 1024) {
    return `${(fileSizeBytes / 1024).toFixed(1)} KB`;
  }
  if (fileSizeBytes < 1024 * 1024 * 1024) {
    return `${(fileSizeBytes / (1024 * 1024)).toFixed(1)} MB`;
  }
  return `${(fileSizeBytes / (1024 * 1024 * 1024)).toFixed(1)} GB`;
}

export function attachmentLabel(attachment: MessageAttachment) {
  if (attachment.kind === "VOICE") {
    return "Voice";
  }
  if (attachment.kind === "AUDIO") {
    return "Audio";
  }
  if (attachment.kind === "IMAGE") {
    return "Photo";
  }
  if (attachment.kind === "VIDEO") {
    return "Video";
  }
  if (attachment.kind === "VIDEO_NOTE") {
    return "Video note";
  }
  if (attachment.kind === "GIF") {
    return "GIF";
  }
  return attachment.originalFileName;
}

export function buildSharedMediaCountLine(buckets: SharedMediaBuckets | null) {
  return `${buckets?.media.length ?? 0} media - ${buckets?.files.length ?? 0} files - ${buckets?.links.length ?? 0} links`;
}

export function buildSharedMediaUpdatedLine(buckets: SharedMediaBuckets | null) {
  return buckets?.loadedAt
    ? `Updated ${new Date(buckets.loadedAt).toLocaleString()}`
    : "Shared content will appear here after sync.";
}

export function normalizeSharedMediaLinkUrl(url: string) {
  const normalized = url.trim();
  if (!normalized) {
    return "";
  }

  if (/^[a-z][a-z0-9+.-]*:\/\//i.test(normalized)) {
    return normalized;
  }

  if (/^(?:www\.)?(?:t\.me|telegram\.me)\//i.test(normalized)) {
    return `https://${normalized.replace(/^https?:\/\//i, "")}`;
  }

  return normalized;
}
