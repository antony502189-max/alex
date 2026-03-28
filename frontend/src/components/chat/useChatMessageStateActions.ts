import { useCallback } from "react";
import { localDatabase } from "../../services/localDatabase";
import { useAppStore } from "../../store/useAppStore";
import type { ChatMember, ChatMessage, ChatSummary, ScheduledMessage } from "../../types";

type UseChatMessageStateActionsParams = {
  chat: ChatSummary;
  currentUserId: string;
  mergeScheduledMessages: (messages: ScheduledMessage[]) => ScheduledMessage[];
  removeMessage: (chatId: string, messageId: string) => void;
  replaceMessage: (chatId: string, messageId: string, message: ChatMessage) => void;
  setCurrentTimeMs: React.Dispatch<React.SetStateAction<number>>;
  setMembers: React.Dispatch<React.SetStateAction<ChatMember[]>>;
  setPinnedMessageId: React.Dispatch<React.SetStateAction<string | null>>;
  setScheduledMessages: React.Dispatch<React.SetStateAction<ScheduledMessage[]>>;
  setSearchResults: React.Dispatch<React.SetStateAction<ChatMessage[]>>;
  upsertChat: (chat: ChatSummary) => void;
  upsertMessage: (message: ChatMessage) => void;
};

export function useChatMessageStateActions({
  chat,
  currentUserId,
  mergeScheduledMessages,
  removeMessage,
  replaceMessage,
  setCurrentTimeMs,
  setMembers,
  setPinnedMessageId,
  setScheduledMessages,
  setSearchResults,
  upsertChat,
  upsertMessage
}: UseChatMessageStateActionsParams) {
  const applyPinnedMessageId = useCallback((nextPinnedMessageId: string | null) => {
    setPinnedMessageId(nextPinnedMessageId);
    const currentChat = useAppStore
      .getState()
      .chats.find((candidate) => candidate.chatId === chat.chatId);
    upsertChat({
      ...(currentChat ?? chat),
      pinnedMessageId: nextPinnedMessageId
    });
  }, [chat, setPinnedMessageId, upsertChat]);

  const syncSearchResult = useCallback((updatedMessage: ChatMessage) => {
    setSearchResults((current) =>
      current.map((message) =>
        message.messageId === updatedMessage.messageId ? updatedMessage : message
      )
    );
  }, [setSearchResults]);

  const syncQueuedMessage = useCallback((queuedMessage: ChatMessage) => {
    upsertMessage(queuedMessage);
    syncSearchResult(queuedMessage);
  }, [syncSearchResult, upsertMessage]);

  const appendScheduledMessage = useCallback((message: ScheduledMessage) => {
    setScheduledMessages((current) => mergeScheduledMessages([...current, message]));
  }, [mergeScheduledMessages, setScheduledMessages]);

  const persistMessage = useCallback((message: ChatMessage) => {
    upsertMessage(message);
    syncSearchResult(message);
    void localDatabase.upsertMessages(currentUserId, [message]).catch(() => undefined);
  }, [currentUserId, syncSearchResult, upsertMessage]);

  const touchMyLastSentAt = useCallback((sentAt: string) => {
    setMembers((current) =>
      current.map((member) =>
        member.userId === currentUserId
          ? {
              ...member,
              lastSentMessageAt: sentAt
            }
          : member
      )
    );
    setCurrentTimeMs(Date.now());
  }, [currentUserId, setCurrentTimeMs, setMembers]);

  const handleQueuedMessageSynced = useCallback((queuedMessageId: string, message: ChatMessage) => {
    replaceMessage(message.chatId, queuedMessageId, message);
    if (message.chatId === chat.chatId) {
      syncSearchResult(message);
      if (message.senderId === currentUserId) {
        touchMyLastSentAt(message.createdAt);
      }
    }
    void localDatabase.upsertMessages(currentUserId, [message]).catch(() => undefined);
  }, [
    chat.chatId,
    currentUserId,
    replaceMessage,
    syncSearchResult,
    touchMyLastSentAt
  ]);

  const handleQueuedMessageDropped = useCallback((queuedMessageId: string, queuedChatId: string) => {
    removeMessage(queuedChatId, queuedMessageId);
    void localDatabase.removeMessage(currentUserId, queuedChatId, queuedMessageId).catch(() => undefined);
    setSearchResults((current) => current.filter((message) => message.messageId !== queuedMessageId));
  }, [currentUserId, removeMessage, setSearchResults]);

  const handleQueuedScheduledSynced = useCallback((
    queuedScheduledMessageId: string,
    message: ScheduledMessage
  ) => {
    if (message.chatId === chat.chatId) {
      setScheduledMessages((current) => {
        const hasQueuedMessage = current.some(
          (item) => item.scheduledMessageId === queuedScheduledMessageId
        );
        return mergeScheduledMessages(
          hasQueuedMessage
            ? current.map((item) =>
                item.scheduledMessageId === queuedScheduledMessageId ? message : item
              )
            : [...current, message]
        );
      });
    }
    void localDatabase.upsertScheduledMessages(currentUserId, [message]).catch(() => undefined);
  }, [
    chat.chatId,
    currentUserId,
    mergeScheduledMessages,
    setScheduledMessages
  ]);

  const handleQueuedScheduledDropped = useCallback((
    queuedScheduledMessageId: string,
    queuedChatId: string
  ) => {
    if (queuedChatId === chat.chatId) {
      setScheduledMessages((current) =>
        current.filter((item) => item.scheduledMessageId !== queuedScheduledMessageId)
      );
    }
    void localDatabase.removeScheduledMessage(
      currentUserId,
      queuedChatId,
      queuedScheduledMessageId
    ).catch(() => undefined);
  }, [chat.chatId, currentUserId, setScheduledMessages]);

  return {
    appendScheduledMessage,
    applyPinnedMessageId,
    handleQueuedMessageDropped,
    handleQueuedMessageSynced,
    handleQueuedScheduledDropped,
    handleQueuedScheduledSynced,
    persistMessage,
    syncQueuedMessage,
    syncSearchResult,
    touchMyLastSentAt
  };
}
