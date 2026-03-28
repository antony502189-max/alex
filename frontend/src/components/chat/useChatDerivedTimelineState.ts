import { useMemo } from "react";
import type {
  ChatMessage,
  MessageSelectionState,
  PinnedMessageHistoryEntry
} from "../../types";

type UseChatDerivedTimelineStateParams = {
  activeThreadRootMessageId: string | null;
  chatId: string;
  chatMessages: ChatMessage[];
  currentUserId: string;
  lastReadAt: string | null;
  messages: ChatMessage[];
  pinnedHistory: PinnedMessageHistoryEntry[];
  pinnedMessageId: string | null;
  replyToMessageId: string | null;
  selectionState: MessageSelectionState;
  unreadCount: number;
};

export function useChatDerivedTimelineState({
  activeThreadRootMessageId,
  chatId,
  chatMessages,
  currentUserId,
  lastReadAt,
  messages,
  pinnedHistory,
  pinnedMessageId,
  replyToMessageId,
  selectionState,
  unreadCount
}: UseChatDerivedTimelineStateParams) {
  const firstUnreadMessage = useMemo(() => {
    if (!lastReadAt || unreadCount <= 0) {
      return null;
    }

    return (
      messages.find(
        (message) =>
          !message.deletedAt &&
          message.senderId !== currentUserId &&
          message.createdAt.localeCompare(lastReadAt) > 0
      ) ?? null
    );
  }, [currentUserId, lastReadAt, messages, unreadCount]);

  const selectedMessages = useMemo(
    () =>
      selectionState.selectedMessageIds.flatMap((messageId) => {
        const selected = messages.find((message) => message.messageId === messageId);
        return selected ? [selected] : [];
      }),
    [messages, selectionState.selectedMessageIds]
  );

  const selectedMessage = useMemo(
    () => selectedMessages[selectedMessages.length - 1] ?? null,
    [selectedMessages]
  );

  const threadRootMessage = useMemo(
    () =>
      activeThreadRootMessageId
        ? chatMessages.find((message) => message.messageId === activeThreadRootMessageId) ?? null
        : null,
    [activeThreadRootMessageId, chatMessages]
  );

  const activeDiscussionRootMessageId =
    activeThreadRootMessageId != null
      ? threadRootMessage?.discussionRootMessageId ?? activeThreadRootMessageId
      : null;

  const activeDiscussionChatId =
    activeThreadRootMessageId != null
      ? threadRootMessage?.discussionChatId ?? chatId
      : null;

  const replyTarget = useMemo(
    () => chatMessages.find((message) => message.messageId === replyToMessageId) ?? null,
    [chatMessages, replyToMessageId]
  );

  const pinnedMessage = useMemo(
    () => chatMessages.find((message) => message.messageId === pinnedMessageId) ?? null,
    [chatMessages, pinnedMessageId]
  );

  const activePinnedHistoryEntry = useMemo(
    () =>
      pinnedHistory.find((entry) => entry.active) ??
      pinnedHistory.find((entry) => entry.messageId === pinnedMessageId) ??
      null,
    [pinnedHistory, pinnedMessageId]
  );

  const pinnedPreviewMessage = pinnedMessage ?? activePinnedHistoryEntry?.message ?? null;

  return {
    activeDiscussionChatId,
    activeDiscussionRootMessageId,
    activePinnedHistoryEntry,
    firstUnreadMessage,
    pinnedMessage,
    pinnedPreviewMessage,
    replyTarget,
    selectedMessage,
    selectedMessages,
    threadRootMessage
  };
}
