import type { ClientFeatureFlags } from "../../config/featureFlags";
import { formatChatAudienceCount } from "../../services/chatAudience";
import { formatPresenceStatus } from "../../services/presence";
import type { ChatListFilter, ChatRowViewModel, ChatSummary } from "../../types";

export type ChatFilter = ChatListFilter;

export type ChatsFeatureFlags = {
  bots: boolean;
  calls: boolean;
  stories: boolean;
};

export function buildChatsFeatureFlags(
  featureFlags?: Partial<ClientFeatureFlags>
): ChatsFeatureFlags {
  return {
    stories: featureFlags?.stories ?? false,
    calls: featureFlags?.calls ?? true,
    bots: featureFlags?.bots ?? false
  };
}

export function buildChatFilterOptions(features: ChatsFeatureFlags) {
  return [
    { id: "ALL" as const, label: "All" },
    { id: "UNREAD" as const, label: "Unread" },
    { id: "PEOPLE" as const, label: "People" },
    { id: "GROUPS" as const, label: "Groups" },
    { id: "CHANNELS" as const, label: "Channels" },
    ...(features.bots ? [{ id: "BOTS" as const, label: "Bots" }] : [])
  ];
}

export function formatAutoDelete(seconds: number | null) {
  if (!seconds) {
    return null;
  }
  if (seconds < 60) {
    return `TTL ${seconds}s`;
  }
  if (seconds < 3600) {
    return `TTL ${Math.round(seconds / 60)}m`;
  }
  if (seconds < 86400) {
    return `TTL ${Math.round(seconds / 3600)}h`;
  }
  return `TTL ${Math.round(seconds / 86400)}d`;
}

export function formatChatMeta(chat: ChatSummary) {
  if (chat.chatType === "DIRECT") {
    return [
      chat.peerIsBot
        ? "bot"
        : formatPresenceStatus(
            { online: chat.peerOnline, lastSeenAt: chat.peerLastSeenAt },
            "status hidden"
          ),
      chat.peerBotSupportsInline ? "inline" : null,
      chat.peerPhoneNumber ?? "phone-hidden"
    ]
      .filter(Boolean)
      .join(" - ");
  }

  if (chat.chatType === "SAVED") {
    return "private notes";
  }

  const parts = [
    chat.publicUsername ? `@${chat.publicUsername}` : null,
    formatChatAudienceCount(chat.chatType, chat.memberCount),
    chat.forumEnabled ? `${chat.topicCount} topic${chat.topicCount === 1 ? "" : "s"}` : null
  ].filter(Boolean);

  return parts.join(" - ");
}

export function buildChatRowViewModel(chat: ChatSummary): ChatRowViewModel {
  const badges: ChatRowViewModel["badges"] = [];

  if (chat.forumEnabled) {
    badges.push({ label: "Forum", tone: "brand" });
  }
  if (chat.chatType === "CHANNEL") {
    badges.push({ label: "Channel", tone: "default" });
  }
  if (chat.chatType === "GROUP") {
    badges.push({ label: "Group", tone: "default" });
  }
  if (chat.pinned) {
    badges.push({ label: "Pinned", tone: "muted" });
  }
  if (chat.markedUnread) {
    badges.push({ label: "Unread", tone: "warning" });
  }
  if (chat.mutedUntil && new Date(chat.mutedUntil).getTime() > Date.now()) {
    badges.push({ label: "Muted", tone: "muted" });
  }
  if (chat.unreadCount > 0) {
    badges.push({ label: String(chat.unreadCount), tone: "success" });
  }
  if (chat.mentionCount > 0) {
    badges.push({ label: `@${chat.mentionCount}`, tone: "warning" });
  }
  if (chat.replyCount > 0) {
    badges.push({ label: `Reply ${chat.replyCount}`, tone: "brand" });
  }

  return {
    chatId: chat.chatId,
    title: chat.title,
    subtitle: formatChatMeta(chat),
    trailingLabel: formatLastActivity(chat.lastMessageAt),
    badges,
    draftLabel: chat.draftText ? `Draft: ${chat.draftText}` : null,
    aboutLabel: chat.draftText ? null : chat.about,
    autoDeleteLabel: formatAutoDelete(chat.autoDeleteSeconds)
  };
}

export function matchesFilter(chat: ChatSummary, filter: ChatFilter) {
  switch (filter) {
    case "UNREAD":
      return chat.unreadCount > 0 || chat.mentionCount > 0 || chat.replyCount > 0;
    case "PEOPLE":
      return chat.chatType === "DIRECT" || chat.chatType === "SAVED";
    case "GROUPS":
      return chat.chatType === "GROUP";
    case "CHANNELS":
      return chat.chatType === "CHANNEL";
    case "BOTS":
      return chat.chatType === "DIRECT" && chat.peerIsBot;
    case "ALL":
    default:
      return true;
  }
}

export function matchesSearch(chat: ChatSummary, query: string) {
  const normalized = query.trim().toLocaleLowerCase();
  if (!normalized) {
    return true;
  }

  const haystack = [
    chat.title,
    chat.about,
    chat.publicUsername,
    chat.peerDisplayName,
    chat.peerPhoneNumber,
    chat.draftText
  ]
    .filter((value): value is string => Boolean(value))
    .join(" ")
    .toLocaleLowerCase();

  return haystack.includes(normalized);
}

export function formatLastActivity(value: string) {
  const timestamp = new Date(value);
  const now = new Date();
  const sameDay =
    timestamp.getFullYear() === now.getFullYear() &&
    timestamp.getMonth() === now.getMonth() &&
    timestamp.getDate() === now.getDate();

  if (sameDay) {
    return timestamp.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
  }

  return timestamp.toLocaleDateString([], { month: "short", day: "numeric" });
}

export function summarizeUnread(chats: ChatSummary[]) {
  return chats.reduce((total, chat) => total + chat.unreadCount, 0);
}
