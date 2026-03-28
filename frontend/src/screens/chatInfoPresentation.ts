import { formatPresenceStatus } from "../services/presence";
import type { ChatSummary } from "../types";

export type ChatInfoPresentation = {
  detailItems: string[];
  heroMeta: string[];
  heroSubtitle: string;
  heroTitle: string;
  manageActionBody: string | null;
  manageActionTitle: string | null;
  profileDescription: string;
  profileLabel: string;
  profileTitle: string;
  quickActionsDescription: string;
  reportDetailsPlaceholder: string;
  safetyDescription: string;
  screenSubtitle: string;
  showBlockUserAction: boolean;
  showClearHistoryAction: boolean;
  showOpenMiniAppAction: boolean;
  showOpenMembersAction: boolean;
  showLeaveChatAction: boolean;
  showReportChatAction: boolean;
  showReportUserAction: boolean;
};

function formatCount(count: number, singular: string, plural: string) {
  return `${count} ${count === 1 ? singular : plural}`;
}

function formatSeconds(value: number | null) {
  if (!value || value <= 0) {
    return null;
  }
  if (value % (24 * 60 * 60) === 0) {
    return formatCount(value / (24 * 60 * 60), "day", "days");
  }
  if (value % (60 * 60) === 0) {
    return formatCount(value / (60 * 60), "hour", "hours");
  }
  if (value % 60 === 0) {
    return formatCount(value / 60, "minute", "minutes");
  }
  return formatCount(value, "second", "seconds");
}

function buildVisibilityLabel(chat: ChatSummary) {
  if (chat.chatType === "DIRECT") {
    return chat.peerIsBot ? "Bot account" : "Private user";
  }
  if (chat.chatType === "GROUP") {
    return chat.publicUsername ? "Public group" : "Private group";
  }
  if (chat.chatType === "CHANNEL") {
    return chat.publicUsername ? "Public channel" : "Private channel";
  }
  return "Saved messages";
}

function buildJoiningLabel(chat: ChatSummary) {
  if (chat.publicUsername) {
    return chat.joinRequiresApproval
      ? "Joining: public requests require approval"
      : "Joining: public join enabled";
  }

  return chat.joinRequiresApproval
    ? "Joining: invite link requests require approval"
    : "Joining: invite only";
}

function buildDirectBotSubtitle(chat: ChatSummary) {
  return [
    "bot account",
    chat.peerBotSupportsInline ? "inline enabled" : null,
    chat.peerBotWebAppUrl ? "mini app available" : null
  ]
    .filter(Boolean)
    .join(" - ");
}

export function getChatInfoPresentation(chat: ChatSummary): ChatInfoPresentation {
  const heroMeta: string[] = [];
  const detailItems: string[] = [];

  if (chat.publicUsername) {
    heroMeta.push(`@${chat.publicUsername}`);
  }

  if (chat.chatType === "DIRECT" && chat.peerPhoneNumber) {
    heroMeta.push(chat.peerPhoneNumber);
  }

  if (chat.chatType === "DIRECT") {
    if (chat.peerIsBot) {
      detailItems.push(
        chat.peerBotSupportsInline ? "Inline mode: enabled" : "Inline mode: unavailable"
      );
      detailItems.push(chat.peerBotWebAppUrl ? "Mini app: available" : "Mini app: unavailable");
    }

    return {
      detailItems,
      heroMeta,
      heroSubtitle: chat.peerIsBot
        ? buildDirectBotSubtitle(chat)
        : formatPresenceStatus(
            {
              online: chat.peerOnline,
              lastSeenAt: chat.peerLastSeenAt
            },
            "last seen hidden"
          ),
      heroTitle: chat.peerDisplayName ?? chat.title,
      manageActionBody: null,
      manageActionTitle: null,
      profileDescription:
        chat.about ??
        (chat.peerIsBot
          ? "This bot has not added a public description yet."
          : "This peer has not added a public bio yet."),
      profileLabel: buildVisibilityLabel(chat),
      profileTitle: chat.peerIsBot ? "Bot profile" : "Profile",
      quickActionsDescription: chat.peerIsBot
        ? "Quick entry points for media, bot shortcuts and related mini app actions."
        : "Quick entry points for media and related bot actions.",
      reportDetailsPlaceholder: "Optional report details",
      safetyDescription: "Use these actions for moderation, reporting and local cleanup.",
      screenSubtitle: chat.peerIsBot ? "Bot profile and controls" : "Direct chat profile and controls",
      showBlockUserAction: Boolean(chat.peerUserId),
      showClearHistoryAction: true,
      showOpenMiniAppAction: chat.peerIsBot && Boolean(chat.peerBotWebAppUrl && chat.peerUserId),
      showOpenMembersAction: false,
      showLeaveChatAction: false,
      showReportChatAction: false,
      showReportUserAction: Boolean(chat.peerUserId)
    };
  }

  if (chat.chatType === "GROUP" || chat.chatType === "CHANNEL") {
    detailItems.push(
      chat.chatType === "GROUP"
        ? `Members: ${formatCount(chat.memberCount, "member", "members")}`
        : `Audience: ${formatCount(chat.memberCount, "subscriber", "subscribers")}`
    );
    detailItems.push(
      chat.publicUsername ? `Public username: @${chat.publicUsername}` : "Visibility: invite-only"
    );
    detailItems.push(buildJoiningLabel(chat));
    detailItems.push(chat.reactionsEnabled ? "Reactions: enabled" : "Reactions: disabled");

    if (chat.chatType === "GROUP") {
      detailItems.push(chat.forumEnabled ? "Topics: enabled" : "Topics: disabled");
    }

    if (chat.chatType === "CHANNEL") {
      detailItems.push(chat.commentsEnabled ? "Comments: enabled" : "Comments: disabled");
      detailItems.push(
        chat.crossPostingEnabled ? "Cross-posting: enabled" : "Cross-posting: disabled"
      );
      if (chat.linkedDiscussionChatTitle) {
        detailItems.push(`Discussion chat: ${chat.linkedDiscussionChatTitle}`);
      }
    }

    const autoDeleteLabel = formatSeconds(chat.autoDeleteSeconds);
    if (autoDeleteLabel) {
      detailItems.push(`Auto-delete: ${autoDeleteLabel}`);
    }

    const slowModeLabel = formatSeconds(chat.slowModeSeconds);
    if (slowModeLabel) {
      detailItems.push(`Slow mode: ${slowModeLabel}`);
    }

    return {
      detailItems,
      heroMeta,
      heroSubtitle:
        chat.chatType === "GROUP"
          ? formatCount(chat.memberCount, "member", "members")
          : formatCount(chat.memberCount, "subscriber", "subscribers"),
      heroTitle: chat.title,
      manageActionBody:
        chat.chatType === "GROUP"
          ? "Open members, invite links, join requests, moderation and profile settings."
          : "Open subscribers, invite links, discussion settings and moderation controls.",
      manageActionTitle: chat.chatType === "GROUP" ? "Manage group" : "Manage channel",
      profileDescription:
        chat.about ??
        (chat.chatType === "GROUP"
          ? "This group has not added a public description yet."
          : "This channel has not added a public description yet."),
      profileLabel: buildVisibilityLabel(chat),
      profileTitle: chat.chatType === "GROUP" ? "Group profile" : "Channel profile",
      quickActionsDescription: "Quick entry points for media, members and chat management.",
      reportDetailsPlaceholder: "Optional report details",
      safetyDescription: "Use these actions to leave, report or clear the local chat history.",
      screenSubtitle:
        chat.chatType === "GROUP" ? "Group profile and controls" : "Channel profile and controls",
      showBlockUserAction: false,
      showClearHistoryAction: true,
      showOpenMiniAppAction: false,
      showOpenMembersAction: true,
      showLeaveChatAction: true,
      showReportChatAction: true,
      showReportUserAction: false
    };
  }

  return {
    detailItems: ["Private notes and forwarded messages stay available only on this account."],
    heroMeta: [],
    heroSubtitle: "Private notes and forwards",
    heroTitle: chat.title,
    manageActionBody: null,
    manageActionTitle: null,
    profileDescription: "Use Saved Messages as a private cloud chat for links, files and drafts.",
    profileLabel: buildVisibilityLabel(chat),
    profileTitle: "Personal storage",
    quickActionsDescription: "Quick entry points for media and local history cleanup.",
    reportDetailsPlaceholder: "",
    safetyDescription: "Use these actions to clean the local visible history for this device state.",
    screenSubtitle: "Personal cloud chat",
    showBlockUserAction: false,
    showClearHistoryAction: true,
    showOpenMiniAppAction: false,
    showOpenMembersAction: false,
    showLeaveChatAction: false,
    showReportChatAction: false,
    showReportUserAction: false
  };
}
