import type { CallHistoryEntry, ChatSummary } from "../../types";
import type { ParsedDeepLink } from "../../navigation/deepLinks";
import { normalizeCallLinkToken as normalizeCallJoinLinkToken } from "../../services/linkTokens";
import { findExactPublicChatMatch } from "../../services/publicChatMatches";

export function formatCallHistoryDate(value: string) {
  return new Date(value).toLocaleString();
}

export function formatCallHistoryDuration(call: CallHistoryEntry) {
  if (!call.answeredAt || !call.endedAt) {
    return null;
  }

  const durationSeconds = Math.max(
    0,
    Math.floor((new Date(call.endedAt).getTime() - new Date(call.answeredAt).getTime()) / 1000)
  );
  const minutes = Math.floor(durationSeconds / 60);
  const seconds = durationSeconds % 60;
  return `${minutes}:${seconds.toString().padStart(2, "0")}`;
}

export function buildCallHistoryStatusLabel(call: CallHistoryEntry) {
  if (call.missed) {
    return "Missed";
  }

  if (!call.answeredAt) {
    if (call.status === "DECLINED") {
      return "Declined";
    }

    return "Canceled";
  }

  return null;
}

export function buildCallHistorySubtitle(call: CallHistoryEntry) {
  const direction = call.direction === "INCOMING" ? "Incoming" : "Outgoing";
  const kind = call.kind === "VIDEO" ? "video" : "voice";
  const statusLabel = buildCallHistoryStatusLabel(call);
  if (statusLabel) {
    return `${statusLabel} ${kind} call`;
  }

  const duration = formatCallHistoryDuration(call);
  if (duration) {
    return `${direction} ${kind} call | ${duration}`;
  }

  return `${direction} ${kind} call`;
}

export function buildCallHistoryMeta(call: CallHistoryEntry) {
  return `${call.mode === "GROUP" ? `${call.participantCount} participants` : "Direct"} | ${formatCallHistoryDate(
    call.endedAt ?? call.answeredAt ?? call.startedAt
  )}`;
}

export function getMissedCallsCount(calls: CallHistoryEntry[]) {
  return calls.filter((call) => call.missed).length;
}

export function formatMissedCallsSummary(count: number) {
  if (count <= 0) {
    return "No missed calls";
  }

  if (count === 1) {
    return "1 missed call";
  }

  return `${count} missed calls`;
}

export function buildCallsHistoryEmptyState(error: string | null) {
  if (error) {
    return {
      description:
        "Recent calls could not be refreshed yet. Reconnect to sync history, or place a call and it will appear here.",
      title: "Call history unavailable"
    };
  }

  return {
    description: "Start a voice or video call from any dialog and it will show up here.",
    title: "No calls yet"
  };
}

export type CallsLinkAction = {
  ctaLabel: string;
  description: string;
  title: string;
};

export function findExactCallsPublicChatMatch(
  chats: ChatSummary[],
  parsedLink: ParsedDeepLink | null
) {
  return findExactPublicChatMatch(chats, parsedLink);
}

export function buildCallsLinkAction(
  parsedLink: ParsedDeepLink | null,
  exactPublicChatMatch: ChatSummary | null = null
): CallsLinkAction | null {
  if (!parsedLink || parsedLink.type === "CALL") {
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

  return {
    ctaLabel: "Open chat",
    description: parsedLink.topicId
      ? `Recognized an app chat link with topic ${parsedLink.topicId}.`
      : "Recognized an app chat link.",
    title: "Open linked chat"
  };
}

export function normalizeCallLinkToken(value: string) {
  return normalizeCallJoinLinkToken(value);
}
