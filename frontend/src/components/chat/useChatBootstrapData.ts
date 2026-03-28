import { useCallback } from "react";
import { api } from "../../services/api";
import { localDatabase } from "../../services/localDatabase";
import { messageOutbox } from "../../services/messageOutbox";
import { scheduledMessageOutbox } from "../../services/scheduledMessageOutbox";
import type {
  ChatMember,
  ChatMessage,
  PinnedMessageHistoryEntry,
  ScheduledMessage
} from "../../types";

type LoadInitialChatDataResult = {
  cachedHistory: ChatMessage[];
  cachedScheduledMessages: ScheduledMessage[];
  history: ChatMessage[];
  members: ChatMember[];
  pinnedHistory: PinnedMessageHistoryEntry[];
  rawScheduledMessages: ScheduledMessage[];
};

type UseChatBootstrapDataParams = {
  activeThreadRootMessageId: string | null;
  chatId: string;
  currentUserId: string;
  handleQueuedMessageDropped: (queuedMessageId: string, queuedChatId: string) => void;
  handleQueuedMessageSynced: (queuedMessageId: string, message: ChatMessage) => void;
  handleQueuedScheduledDropped: (
    queuedScheduledMessageId: string,
    queuedChatId: string
  ) => void;
  handleQueuedScheduledSynced: (
    queuedScheduledMessageId: string,
    message: ScheduledMessage
  ) => void;
  mergeScheduledMessages: (messages: ScheduledMessage[]) => ScheduledMessage[];
  pageSize: number;
  setError: (value: string | null) => void;
  setLoadingPinnedHistory: React.Dispatch<React.SetStateAction<boolean>>;
  setPinnedHistory: React.Dispatch<React.SetStateAction<PinnedMessageHistoryEntry[]>>;
  setScheduledMessages: React.Dispatch<React.SetStateAction<ScheduledMessage[]>>;
  token: string;
  topicId: string | null;
};

export function useChatBootstrapData({
  activeThreadRootMessageId,
  chatId,
  currentUserId,
  handleQueuedMessageDropped,
  handleQueuedMessageSynced,
  handleQueuedScheduledDropped,
  handleQueuedScheduledSynced,
  mergeScheduledMessages,
  pageSize,
  setError,
  setLoadingPinnedHistory,
  setPinnedHistory,
  setScheduledMessages,
  token,
  topicId
}: UseChatBootstrapDataParams) {
  const flushPendingOutbox = useCallback(async () => {
    await messageOutbox.flush(token, currentUserId, {
      onSynced: handleQueuedMessageSynced,
      onDropped: handleQueuedMessageDropped
    });
    await scheduledMessageOutbox.flush(token, currentUserId, {
      onSynced: handleQueuedScheduledSynced,
      onDropped: handleQueuedScheduledDropped
    });
  }, [
    currentUserId,
    handleQueuedMessageDropped,
    handleQueuedMessageSynced,
    handleQueuedScheduledDropped,
    handleQueuedScheduledSynced,
    token
  ]);

  const syncScheduledMessages = useCallback(async () => {
    const rawScheduledMessages = await api.getScheduledMessages(
      token,
      chatId,
      topicId,
      activeThreadRootMessageId
    );
    setScheduledMessages(mergeScheduledMessages(rawScheduledMessages));
    await localDatabase.replaceScheduledMessages(
      currentUserId,
      chatId,
      rawScheduledMessages,
      topicId,
      activeThreadRootMessageId
    );
    return rawScheduledMessages;
  }, [
    activeThreadRootMessageId,
    chatId,
    currentUserId,
    mergeScheduledMessages,
    setScheduledMessages,
    token,
    topicId
  ]);

  const refreshPinnedHistory = useCallback(async () => {
    setLoadingPinnedHistory(true);
    try {
      const nextPinnedHistory = await api.getPinnedMessages(token, chatId, 20);
      setPinnedHistory(nextPinnedHistory);
      return nextPinnedHistory;
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Unable to load pinned history");
      return [];
    } finally {
      setLoadingPinnedHistory(false);
    }
  }, [chatId, setError, setLoadingPinnedHistory, setPinnedHistory, token]);

  const loadInitialChatData = useCallback(async (): Promise<LoadInitialChatDataResult> => {
    const cachedHistory = await localDatabase.getMessages(
      currentUserId,
      chatId,
      pageSize,
      topicId,
      activeThreadRootMessageId
    );
    const cachedScheduledMessages = await localDatabase.getScheduledMessages(
      currentUserId,
      chatId,
      topicId,
      activeThreadRootMessageId
    );

    await flushPendingOutbox().catch(() => undefined);
    const [history, members, rawScheduledMessages, pinnedHistory] = await Promise.all([
      api.getMessages(token, chatId, pageSize, topicId, activeThreadRootMessageId),
      api.getChatMembers(token, chatId),
      api.getScheduledMessages(token, chatId, topicId, activeThreadRootMessageId),
      api.getPinnedMessages(token, chatId, 20).catch(() => [])
    ]);

    return {
      cachedHistory,
      cachedScheduledMessages,
      history,
      members,
      pinnedHistory,
      rawScheduledMessages
    };
  }, [
    activeThreadRootMessageId,
    chatId,
    currentUserId,
    flushPendingOutbox,
    pageSize,
    token,
    topicId
  ]);

  return {
    flushPendingOutbox,
    loadInitialChatData,
    refreshPinnedHistory,
    syncScheduledMessages
  };
}
