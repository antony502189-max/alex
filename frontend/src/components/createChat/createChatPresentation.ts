import { formatPresenceStatus } from "../../services/presence";
import type { UserSearchResult } from "../../types";

export type CreateChatMode = "direct" | "group" | "channel";

export function buildCreateChatTitle(mode: CreateChatMode) {
  if (mode === "group") {
    return "New group";
  }
  if (mode === "channel") {
    return "New channel";
  }
  return "New direct chat";
}

export function buildCreateChatSubtitle(mode: CreateChatMode) {
  if (mode === "direct") {
    return "Search by phone or display name";
  }
  return "Choose people first, then finish the chat settings";
}

export function buildCollectionTitlePlaceholder(mode: Exclude<CreateChatMode, "direct">) {
  return mode === "channel" ? "Channel title" : "Group title";
}

export function buildCollectionAboutPlaceholder(mode: Exclude<CreateChatMode, "direct">) {
  return mode === "channel" ? "Channel description" : "Group description";
}

export function canSubmitCollectionChat(
  mode: Exclude<CreateChatMode, "direct">,
  title: string,
  selectedCount: number
) {
  if (!title.trim()) {
    return false;
  }

  if (mode === "group" && selectedCount === 0) {
    return false;
  }

  return true;
}

export function buildCreateChatSubmitLabel(
  mode: Exclude<CreateChatMode, "direct">,
  selectedCount: number,
  submitting: boolean
) {
  if (submitting) {
    return "Creating...";
  }

  return mode === "channel"
    ? `Create channel (${selectedCount})`
    : `Create group (${selectedCount})`;
}

export function buildCreateChatUserMeta(user: UserSearchResult) {
  return [
    [
      user.username ? `@${user.username}` : null,
      user.bot ? "bot" : null
    ]
      .filter(Boolean)
      .join(" - "),
    [
      user.bot
        ? "bot"
        : formatPresenceStatus(
            { online: user.online, lastSeenAt: user.lastSeenAt },
            "status hidden"
          ),
      user.phoneNumber ?? "phone hidden"
    ]
      .filter(Boolean)
      .join(" - ")
  ].filter((line) => line.length > 0);
}

export function buildCreateChatEmptyState(query: string) {
  return query.trim().length < 2
    ? "Start typing at least two characters to find users."
    : "No users matched your search.";
}
