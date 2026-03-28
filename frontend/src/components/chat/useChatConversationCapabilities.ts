import { useMemo } from "react";
import { formatCooldown } from "./chatScreenUtils";
import type { ChatMember, ChatSummary } from "../../types";

type UseChatConversationCapabilitiesParams = {
  chat: ChatSummary;
  currentTimeMs: number;
  currentUserId: string;
  members: ChatMember[];
  topicClosed: boolean;
};

export function useChatConversationCapabilities({
  chat,
  currentTimeMs,
  currentUserId,
  members,
  topicClosed
}: UseChatConversationCapabilitiesParams) {
  const myMembership = useMemo(
    () => members.find((member) => member.userId === currentUserId) ?? null,
    [currentUserId, members]
  );

  const memberRestricted = Boolean(myMembership && !myMembership.canSendMessages);
  const channelPostingDisabled =
    chat.chatType === "CHANNEL" && !Boolean(myMembership?.canPostMessages);

  const slowModeEndsAt = useMemo(() => {
    if (
      !chat.slowModeSeconds ||
      chat.chatType === "DIRECT" ||
      chat.chatType === "SAVED" ||
      !myMembership?.lastSentMessageAt ||
      myMembership.role === "OWNER" ||
      myMembership.role === "ADMIN"
    ) {
      return null;
    }
    const nextAllowedAt =
      new Date(myMembership.lastSentMessageAt).getTime() + chat.slowModeSeconds * 1000;
    return nextAllowedAt > currentTimeMs ? nextAllowedAt : null;
  }, [
    chat.chatType,
    chat.slowModeSeconds,
    currentTimeMs,
    myMembership?.lastSentMessageAt,
    myMembership?.role
  ]);

  const restrictionLabel = memberRestricted
    ? myMembership?.restrictedUntil
      ? `Posting restricted until ${new Date(myMembership.restrictedUntil).toLocaleString()}`
      : "Posting restricted by an admin"
    : null;

  const slowModeLabel = slowModeEndsAt
    ? `Slow mode active. You can send again in ${formatCooldown(
        slowModeEndsAt - currentTimeMs
      )}.`
    : null;

  const canPost =
    !topicClosed &&
    !memberRestricted &&
    !channelPostingDisabled &&
    !slowModeEndsAt;

  const canPinMessages =
    chat.chatType === "DIRECT" || Boolean(myMembership?.canPinMessages);

  const reactionsEnabled = chat.reactionsEnabled !== false;

  const myAnonymousAdmin =
    (chat.chatType === "GROUP" || chat.chatType === "CHANNEL") &&
    Boolean(myMembership?.anonymousAdmin);

  const optimisticAuthor = useMemo(
    () => ({
      anonymousSender: myAnonymousAdmin,
      displaySenderName: myAnonymousAdmin ? chat.title : null,
      displaySenderPhotoAccessExpiresAt: myAnonymousAdmin
        ? chat.photoAccessExpiresAt
        : null,
      displaySenderPhotoUrl: myAnonymousAdmin ? chat.photoUrl : null
    }),
    [chat.photoAccessExpiresAt, chat.photoUrl, chat.title, myAnonymousAdmin]
  );

  return {
    canPinMessages,
    canPost,
    channelPostingDisabled,
    memberRestricted,
    myAnonymousAdmin,
    myMembership,
    optimisticAuthor,
    reactionsEnabled,
    restrictionLabel,
    slowModeEndsAt,
    slowModeLabel
  };
}
