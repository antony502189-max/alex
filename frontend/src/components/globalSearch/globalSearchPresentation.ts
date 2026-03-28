import type { ParsedDeepLink } from "../../navigation/deepLinks";
import { formatChatAudienceCount } from "../../services/chatAudience";
import { formatPresenceStatus } from "../../services/presence";
export { findExactPublicChatMatch } from "../../services/publicChatMatches";
import type {
  ChatSummary,
  GlobalMessageSearchResult,
  GlobalSearchResponse,
  UserSearchResult
} from "../../types";

export function hasGlobalSearchResults(results: GlobalSearchResponse | null) {
  return Boolean(
    results &&
      (results.users.length > 0 || results.chats.length > 0 || results.messages.length > 0)
  );
}

export function buildGlobalSearchSummary(results: GlobalSearchResponse | null) {
  if (!results) {
    return null;
  }

  const total = results.users.length + results.chats.length + results.messages.length;
  return `${total} result${total === 1 ? "" : "s"}`;
}

export type GlobalSearchLinkAction = {
  ctaLabel: string;
  description: string;
  title: string;
};

export function buildGlobalSearchLinkAction(
  parsedLink: ParsedDeepLink | null,
  exactPublicChatMatch: ChatSummary | null = null
): GlobalSearchLinkAction | null {
  if (!parsedLink) {
    return null;
  }

  if (parsedLink.type === "JOIN") {
    if (exactPublicChatMatch) {
      return {
        ctaLabel: "Open chat",
        description: `Recognized a public username for a chat already available locally: ${parsedLink.token}`,
        title: "Open linked chat"
      };
    }

    return {
      ctaLabel: "Open join flow",
      description: parsedLink.token.startsWith("@")
        ? `Recognized a public chat username: ${parsedLink.token}`
        : "Recognized an invite link. Open the join flow to preview or request access.",
      title: "Open chat link"
    };
  }

  if (parsedLink.type === "CALL") {
    return {
      ctaLabel: "Join call",
      description: "Recognized a call link. Jump straight into the active room.",
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

export function buildGlobalSearchInfoText(
  query: string,
  loading: boolean,
  resultSummary: string | null,
  linkAction?: GlobalSearchLinkAction | null
) {
  if (query.trim().length < 2) {
    return "Type at least 2 characters to search across users, chats, and messages.";
  }

  if (linkAction) {
    return "Recognized a link or username. Use the quick action below.";
  }

  if (loading) {
    return "Searching...";
  }

  return resultSummary ?? `No results for "${query.trim()}"`;
}

export function buildGlobalSearchUserMeta(user: UserSearchResult) {
  return [
    [
      user.username ? `@${user.username}` : "no username",
      user.bot ? "bot" : null
    ]
      .filter(Boolean)
      .join(" - "),
    [
      user.bot
        ? "bot account"
        : formatPresenceStatus(
            { online: user.online, lastSeenAt: user.lastSeenAt },
            "status hidden"
          ),
      user.phoneNumber ?? "phone hidden"
    ]
      .filter(Boolean)
      .join(" - ")
  ];
}

export function describeGlobalSearchChat(chat: ChatSummary) {
  if (chat.chatType === "DIRECT") {
    return [
      chat.peerIsBot
        ? "bot"
        : formatPresenceStatus(
            { online: chat.peerOnline, lastSeenAt: chat.peerLastSeenAt },
            "status hidden"
          ),
      chat.peerPhoneNumber ?? chat.peerDisplayName ?? "Direct chat"
    ]
      .filter(Boolean)
      .join(" - ");
  }

  if (chat.chatType === "SAVED") {
    return "Private notes";
  }

  const parts = [
    chat.publicUsername ? `@${chat.publicUsername}` : null,
    formatChatAudienceCount(chat.chatType, chat.memberCount),
    chat.forumEnabled ? `${chat.topicCount} topics` : null
  ].filter(Boolean);

  return parts.join(" - ");
}

export function describeGlobalSearchMessage(result: GlobalMessageSearchResult) {
  const message = result.message;

  if (message.serviceMessage?.text) {
    return message.serviceMessage.text;
  }

  if (message.messageType === "LOCATION") {
    const title = message.location?.title?.trim();
    const address = message.location?.address?.trim();
    if (title && address) {
      return `${title} - ${address}`;
    }
    if (title) {
      return title;
    }
    if (address) {
      return address;
    }
    return "Location";
  }

  if (message.messageType === "CONTACT_CARD") {
    const fullName = [message.contactCard?.firstName, message.contactCard?.lastName]
      .filter((part) => !!part)
      .join(" ")
      .trim();
    return fullName || message.contactCard?.phoneNumber || "Contact";
  }

  if (message.poll) {
    return `Poll: ${message.poll.question}`;
  }

  if (message.sticker) {
    return `Sticker: ${message.sticker.emoji} ${message.sticker.label}`;
  }

  if (message.caption) {
    return message.caption;
  }

  if (message.text) {
    return message.text;
  }

  if (message.attachments.length > 0) {
    return message.attachments.length > 1
      ? "Attachment album"
      : message.attachments[0].kind.toLowerCase().replace("_", " ");
  }

  return "Message";
}
