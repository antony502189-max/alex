import type {
  JoinChatResult,
  PublicChatDiscovery
} from "../../types";
import type { ParsedDeepLink } from "../../navigation/deepLinks";
import { formatChatAudienceCount } from "../../services/chatAudience";
import { normalizeInviteToken as normalizeInviteLinkToken } from "../../services/linkTokens";

export function normalizeInviteToken(value: string) {
  return normalizeInviteLinkToken(value);
}

export function shouldDiscoverPublicChats(value: string) {
  const normalized = normalizeInviteToken(value);
  if (normalized.length < 2) {
    return false;
  }
  if (normalized.includes("/")) {
    return false;
  }
  return true;
}

export function getPublicChatDiscoveryQuery(value: string) {
  const normalized = normalizeInviteToken(value);
  return normalized.startsWith("@") ? normalized.slice(1) : normalized;
}

export function buildJoinRequestStatusMessage(result: JoinChatResult) {
  return `Join request sent to ${result.title}${
    result.publicUsername ? ` (@${result.publicUsername})` : ""
  }.`;
}

export type JoinChatLinkAction = {
  ctaLabel: string;
  description: string;
  title: string;
};

export function buildJoinChatLinkAction(parsedLink: ParsedDeepLink | null): JoinChatLinkAction | null {
  if (!parsedLink || parsedLink.type === "JOIN") {
    return null;
  }

  if (parsedLink.type === "CALL") {
    return {
      ctaLabel: "Join call",
      description: "Recognized a call link. Open the calls flow instead of trying to join it as a chat.",
      title: "Open call link"
    };
  }

  return {
    ctaLabel: "Open chat",
    description: parsedLink.topicId
      ? `Recognized an app chat link with topic ${parsedLink.topicId}.`
      : "Recognized an app chat link.",
    title: "Open linked chat"
  };
}

export function buildDiscoveryMetaLines(chat: PublicChatDiscovery) {
  return [
    chat.publicUsername ? `@${chat.publicUsername}` : chat.chatType,
    `${formatChatAudienceCount(chat.chatType, chat.memberCount)}${
      chat.joined ? " | already joined" : ""
    }${chat.joinRequiresApproval ? " | approval" : ""}`
  ];
}

export function getDiscoveryActionLabel(chat: PublicChatDiscovery) {
  if (chat.joined) {
    return "Open";
  }

  return chat.joinRequiresApproval ? "Request access" : "Join";
}

export function findExactPublicChatDiscovery(
  discoveries: PublicChatDiscovery[],
  normalizedInviteToken: string
) {
  if (!normalizedInviteToken.startsWith("@")) {
    return null;
  }

  const normalizedUsername = normalizedInviteToken.slice(1).toLocaleLowerCase();
  return discoveries.find((chat) => chat.publicUsername?.toLocaleLowerCase() === normalizedUsername) ?? null;
}

export function getJoinFieldActionLabel(discovery: PublicChatDiscovery | null) {
  if (!discovery) {
    return "Join chat";
  }

  if (discovery.joined) {
    return "Open chat";
  }

  return discovery.joinRequiresApproval ? "Request access" : "Join chat";
}
