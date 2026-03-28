import { formatAutoDelete } from "./chatMessageHelpers";
import { formatChatAudienceCount } from "../../services/chatAudience";
import type {
  ChatMessage,
  ChatSummary,
  ForumTopic,
  PinnedMessageHistoryEntry,
  ScheduledMessage
} from "../../types";

type MessageDescriber = (message: ChatMessage | ScheduledMessage) => string;

type ChatHeaderPresentationArgs = {
  activeThreadRootMessageId: string | null;
  chat: ChatSummary;
  directPresenceLabel: string;
  memberCount: number;
  threadCommentCount: number | null | undefined;
  threadTitle?: string | null;
  topic?: ForumTopic | null;
};

export function buildChatHeaderPresentation({
  activeThreadRootMessageId,
  chat,
  directPresenceLabel,
  memberCount,
  threadCommentCount,
  threadTitle,
  topic
}: ChatHeaderPresentationArgs) {
  const baseTitle = topic ? `${topic.iconEmoji ? `${topic.iconEmoji} ` : ""}${topic.title}` : chat.title;
  const title = activeThreadRootMessageId ? threadTitle ?? "Comments" : baseTitle;

  const subtitle = activeThreadRootMessageId
    ? [
        chat.title,
        threadCommentCount ? `${threadCommentCount} comments` : "comment thread",
        formatAutoDelete(chat.autoDeleteSeconds)
      ]
        .filter(Boolean)
        .join(" | ")
    : topic
      ? [
          chat.title,
          topic.closed ? "closed topic" : "open topic",
          formatAutoDelete(chat.autoDeleteSeconds)
        ]
          .filter(Boolean)
          .join(" - ")
      : chat.chatType === "SAVED"
        ? ["private notes", formatAutoDelete(chat.autoDeleteSeconds)]
            .filter(Boolean)
            .join(" - ")
        : chat.chatType === "DIRECT"
          ? [
              directPresenceLabel,
              chat.peerBotSupportsInline ? "inline" : null,
              chat.publicUsername ? `@${chat.publicUsername}` : null,
              chat.about,
              chat.peerPhoneNumber ?? "phone-hidden",
              formatAutoDelete(chat.autoDeleteSeconds)
            ]
              .filter(Boolean)
              .join(" - ")
          : [
              chat.publicUsername ? `@${chat.publicUsername}` : null,
              chat.about,
              formatChatAudienceCount(chat.chatType, memberCount),
              formatAutoDelete(chat.autoDeleteSeconds)
            ]
              .filter(Boolean)
              .join(" - ");

  return { subtitle, title };
}

export function getMuteToggleLabel(mutedUntil: string | null, now = Date.now()) {
  return mutedUntil && new Date(mutedUntil).getTime() > now ? "Unmute" : "Mute 1h";
}

export function getArchiveToggleLabel(archived: boolean) {
  return archived ? "Unarchive" : "Archive";
}

export function getPinnedHistoryToggleLabel(showPinnedHistory: boolean, pinnedCount: number) {
  if (showPinnedHistory) {
    return "Hide pins";
  }
  return `Pins${pinnedCount > 0 ? ` (${pinnedCount})` : ""}`;
}

export function getPinnedPreviewText(args: {
  describeMessage: MessageDescriber;
  loadingPinnedHistory: boolean;
  pinnedPreviewMessage: ChatMessage | null;
}) {
  const { describeMessage, loadingPinnedHistory, pinnedPreviewMessage } = args;
  if (loadingPinnedHistory && !pinnedPreviewMessage) {
    return "Loading pinned message...";
  }
  if (pinnedPreviewMessage?.deletedAt) {
    return "Message deleted";
  }
  if (pinnedPreviewMessage) {
    return describeMessage(pinnedPreviewMessage);
  }
  return "Pinned message is outside the loaded window";
}

export function formatPinnedHistoryEntryPreview(
  entry: PinnedMessageHistoryEntry,
  describeMessage: MessageDescriber
) {
  if (entry.message?.deletedAt) {
    return "Message deleted";
  }
  if (entry.message) {
    return describeMessage(entry.message);
  }
  return "Message preview unavailable";
}

export function formatPinnedHistoryEntryMeta(entry: PinnedMessageHistoryEntry) {
  return `by ${entry.pinnedByDisplayName}${
    entry.unpinnedAt ? ` - replaced ${new Date(entry.unpinnedAt).toLocaleString()}` : ""
  }`;
}

export function getScheduledPanelTitle(args: {
  activeThreadRootMessageId: string | null;
  threadTitle?: string | null;
  topic?: ForumTopic | null;
}) {
  const { activeThreadRootMessageId, threadTitle, topic } = args;
  if (activeThreadRootMessageId) {
    return threadTitle ? `Scheduled in ${threadTitle}` : "Scheduled comments";
  }
  return topic ? `Scheduled in ${topic.title}` : "Scheduled messages";
}

export function formatScheduledMessageMeta(message: ScheduledMessage) {
  return [
    new Date(message.scheduledAt).toLocaleString(),
    message.status === "WAITING_ONLINE" ? "when recipient is online" : null,
    message.silent ? "silent" : null,
    message.status === "QUEUED" ? "queued offline" : null
  ]
    .filter(Boolean)
    .join(" - ");
}

export function getReplyPanelTitle(editingMessageId: string | null, activeThreadRootMessageId: string | null) {
  if (editingMessageId) {
    return "Editing message";
  }
  if (activeThreadRootMessageId) {
    return "Comment thread";
  }
  return "Replying";
}
