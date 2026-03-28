import React from "react";
import { ChatsListScreenContent } from "../components/chats/ChatsListScreenContent";
import { AppScreen } from "../components/ui/AppScreen";
import { useChatsListController } from "../components/chats/useChatsListController";
import type { ClientFeatureFlags } from "../config/featureFlags";
import { appColors } from "../theme/tokens";
import type { ChatSummary } from "../types";

type ChatsListScreenProps = {
  featureFlags?: Partial<ClientFeatureFlags>;
  onOpenArchived: () => void;
  onOpenCalls: () => void;
  onOpenChat: (chat: ChatSummary) => void;
  onOpenContacts: () => void;
  onOpenFolders: () => void;
  onOpenGlobalSearch: () => void;
  onOpenJoinByLink: () => void;
  onOpenProfile: () => void;
  onOpenSavedMessages: () => void;
  onOpenStories: () => void;
  onCreateChannel: () => void;
  onCreateDirect: () => void;
  onCreateGroup: () => void;
  onCreateStory: () => void;
};

type ChatFilter = "ALL" | "UNREAD" | "PEOPLE" | "GROUPS" | "CHANNELS" | "BOTS";

type QuickAction = {
  key: string;
  title: string;
  caption: string;
  onPress: () => void;
  tone?: "blue" | "dark" | "warm";
};

function formatAutoDelete(seconds: number | null) {
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

function formatChatMeta(chat: ChatSummary) {
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
    `${chat.memberCount} members`,
    chat.forumEnabled ? `${chat.topicCount} topic${chat.topicCount === 1 ? "" : "s"}` : null
  ].filter(Boolean);

  return parts.join(" - ");
}

function matchesFilter(chat: ChatSummary, filter: ChatFilter) {
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

function matchesSearch(chat: ChatSummary, query: string) {
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

function formatLastActivity(value: string) {
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

function summarizeUnread(chats: ChatSummary[]) {
  return chats.reduce((total, chat) => total + chat.unreadCount, 0);
}

export function ChatsListScreen({
  featureFlags,
  onOpenArchived,
  onOpenCalls,
  onOpenChat,
  onOpenContacts,
  onOpenFolders,
  onOpenGlobalSearch,
  onOpenJoinByLink,
  onOpenProfile,
  onOpenSavedMessages,
  onOpenStories,
  onCreateChannel,
  onCreateDirect,
  onCreateGroup,
  onCreateStory
}: ChatsListScreenProps) {
  const controller = useChatsListController({
    featureFlags,
    onCreateDirect,
    onOpenCalls,
    onOpenContacts,
    onOpenProfile,
    onOpenSavedMessages,
    onOpenStories
  });

  return (
    <AppScreen
      backgroundColor={appColors.surfaceMuted}
      paddingHorizontal="lg"
      paddingTop="md"
    >
      <ChatsListScreenContent
        controller={controller}
        onCreateChannel={onCreateChannel}
        onCreateGroup={onCreateGroup}
        onCreateStory={onCreateStory}
        onOpenArchived={onOpenArchived}
        onOpenChat={onOpenChat}
        onOpenFolders={onOpenFolders}
        onOpenGlobalSearch={onOpenGlobalSearch}
        onOpenJoinByLink={onOpenJoinByLink}
      />
    </AppScreen>
  );
}
