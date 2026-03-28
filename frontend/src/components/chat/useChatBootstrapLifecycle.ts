import { useEffect } from "react";
import { api } from "../../services/api";
import { cleanupStagedAttachment } from "../../services/attachmentDrafts";
import { localDatabase } from "../../services/localDatabase";
import { wsService } from "../../services/ws";
import type {
  ChatMember,
  ChatReadEvent,
  ChatMessage,
  MessageAttachment,
  MessageTextEntity,
  PinnedMessageHistoryEntry,
  PinMessageEvent,
  ScheduledMessage
} from "../../types";
import type { TypingEvent } from "../../types";
import { isQueuedUploadAttachment } from "./chatAttachmentHelpers";

type LoadInitialChatDataResult = {
  cachedHistory: ChatMessage[];
  cachedScheduledMessages: ScheduledMessage[];
  history: ChatMessage[];
  members: ChatMember[];
  pinnedHistory: PinnedMessageHistoryEntry[];
  rawScheduledMessages: ScheduledMessage[];
};

type UseChatBootstrapLifecycleParams = {
  activeThreadRootMessageId: string | null;
  chatId: string;
  chatPinnedMessageId: string | null;
  draftText: string | null | undefined;
  currentUserId: string;
  flushPendingOutbox: () => Promise<void>;
  handlePinEvent: (event: PinMessageEvent) => void;
  handleReadEvent: (event: ChatReadEvent) => void;
  handleTypingEvent: (event: TypingEvent) => void;
  isTypingRef: React.MutableRefObject<boolean>;
  loadInitialChatData: () => Promise<LoadInitialChatDataResult>;
  mergeScheduledMessages: (messages: ScheduledMessage[]) => ScheduledMessage[];
  pageSize: number;
  pendingAttachments: MessageAttachment[];
  pendingAttachmentsRef: React.MutableRefObject<MessageAttachment[]>;
  persistedDraftRef: React.MutableRefObject<string>;
  resetStructuredComposerState: () => void;
  resetTimelineSearchState: () => void;
  setChatMessages: (chatId: string, messages: ChatMessage[]) => void;
  setComposerSelection: React.Dispatch<React.SetStateAction<{ start: number; end: number }>>;
  setDraft: React.Dispatch<React.SetStateAction<string>>;
  setDraftEntities: React.Dispatch<React.SetStateAction<MessageTextEntity[]>>;
  setError: (value: string | null) => void;
  setHasMoreHistory: React.Dispatch<React.SetStateAction<boolean>>;
  setLoadingHistory: React.Dispatch<React.SetStateAction<boolean>>;
  setLoadingPinnedHistory: React.Dispatch<React.SetStateAction<boolean>>;
  setMembers: React.Dispatch<React.SetStateAction<ChatMember[]>>;
  setPendingAttachments: React.Dispatch<React.SetStateAction<MessageAttachment[]>>;
  setPinnedHistory: React.Dispatch<React.SetStateAction<PinnedMessageHistoryEntry[]>>;
  setPinnedMessageId: React.Dispatch<React.SetStateAction<string | null>>;
  setScheduledMessages: React.Dispatch<React.SetStateAction<ScheduledMessage[]>>;
  setSendSilently: React.Dispatch<React.SetStateAction<boolean>>;
  setShowPinnedHistory: React.Dispatch<React.SetStateAction<boolean>>;
  setShowScheduledPanel: React.Dispatch<React.SetStateAction<boolean>>;
  syncScheduledMessages: () => Promise<ScheduledMessage[]>;
  token: string;
  topicId: string | null;
  typingResetRef: React.MutableRefObject<ReturnType<typeof setTimeout> | null>;
  typingTimeoutsRef: React.MutableRefObject<Record<string, ReturnType<typeof setTimeout>>>;
};

export function useChatBootstrapLifecycle({
  activeThreadRootMessageId,
  chatId,
  chatPinnedMessageId,
  currentUserId,
  draftText,
  flushPendingOutbox,
  handlePinEvent,
  handleReadEvent,
  handleTypingEvent,
  isTypingRef,
  loadInitialChatData,
  mergeScheduledMessages,
  pageSize,
  pendingAttachments,
  pendingAttachmentsRef,
  persistedDraftRef,
  resetStructuredComposerState,
  resetTimelineSearchState,
  setChatMessages,
  setComposerSelection,
  setDraft,
  setDraftEntities,
  setError,
  setHasMoreHistory,
  setLoadingHistory,
  setLoadingPinnedHistory,
  setMembers,
  setPendingAttachments,
  setPinnedHistory,
  setPinnedMessageId,
  setScheduledMessages,
  setSendSilently,
  setShowPinnedHistory,
  setShowScheduledPanel,
  syncScheduledMessages,
  token,
  topicId,
  typingResetRef,
  typingTimeoutsRef
}: UseChatBootstrapLifecycleParams) {
  useEffect(() => {
    pendingAttachmentsRef.current = pendingAttachments;
  }, [pendingAttachments, pendingAttachmentsRef]);

  useEffect(() => {
    let cancelled = false;
    let cachedHistory: ChatMessage[] = [];
    let cachedScheduledMessages: ScheduledMessage[] = [];

    async function loadState() {
      setLoadingHistory(true);
      setError(null);
      try {
        const {
          cachedHistory: nextCachedHistory,
          cachedScheduledMessages: nextCachedScheduledMessages,
          history,
          members: nextMembers,
          pinnedHistory: nextPinnedHistory,
          rawScheduledMessages
        } = await loadInitialChatData();
        cachedHistory = nextCachedHistory;
        cachedScheduledMessages = nextCachedScheduledMessages;
        if (!cancelled && cachedHistory.length > 0) {
          setChatMessages(chatId, cachedHistory);
        }
        if (!cancelled && cachedScheduledMessages.length > 0) {
          setScheduledMessages(cachedScheduledMessages);
        }

        if (!cancelled) {
          setChatMessages(chatId, history);
          void localDatabase.upsertMessages(currentUserId, history).catch(() => undefined);
          setMembers(nextMembers);
          setScheduledMessages(mergeScheduledMessages(rawScheduledMessages));
          setPinnedHistory(nextPinnedHistory);
          void localDatabase.replaceScheduledMessages(
            currentUserId,
            chatId,
            rawScheduledMessages,
            topicId,
            activeThreadRootMessageId
          ).catch(() => undefined);
          setHasMoreHistory(history.length === pageSize);
        }
      } catch (loadError) {
        if (!cancelled) {
          setError(
            cachedHistory.length > 0 || cachedScheduledMessages.length > 0
              ? "Offline mode. Showing cached messages."
              : loadError instanceof Error
                ? loadError.message
                : "Unable to load chat"
          );
        }
      } finally {
        if (!cancelled) {
          setLoadingHistory(false);
          setLoadingPinnedHistory(false);
        }
      }
    }

    const unsubscribe = wsService.subscribeToChat(chatId, {
      onTyping: handleTypingEvent,
      onRead: handleReadEvent,
      onPin: handlePinEvent
    });
    const unsubscribeConnection = wsService.onConnectionChange((connected) => {
      if (!connected || cancelled) {
        return;
      }

      void flushPendingOutbox()
        .then(() => syncScheduledMessages())
        .catch(() => undefined);
    });

    setPinnedMessageId(chatPinnedMessageId);
    resetTimelineSearchState();
    setPendingAttachments([]);
    setShowScheduledPanel(false);
    resetStructuredComposerState();
    setPinnedHistory([]);
    setShowPinnedHistory(false);
    setSendSilently(false);
    persistedDraftRef.current = draftText?.trim() ?? "";
    setDraft(draftText ?? "");
    setDraftEntities([]);
    setComposerSelection({ start: 0, end: 0 });
    void loadState();

    return () => {
      cancelled = true;
      unsubscribe();
      unsubscribeConnection();
      if (isTypingRef.current) {
        void api.sendTyping(token, chatId, false).catch(() => undefined);
      }
      if (typingResetRef.current) {
        clearTimeout(typingResetRef.current);
      }
      Object.values(typingTimeoutsRef.current).forEach((timeoutId) => {
        clearTimeout(timeoutId);
      });
      typingTimeoutsRef.current = {};
      void Promise.all(
        pendingAttachmentsRef.current
          .filter((attachment) => isQueuedUploadAttachment(attachment))
          .map((attachment) => cleanupStagedAttachment(attachment).catch(() => undefined))
      );
    };
  }, [
    activeThreadRootMessageId,
    chatId,
    chatPinnedMessageId,
    currentUserId,
    draftText,
    flushPendingOutbox,
    handlePinEvent,
    handleReadEvent,
    handleTypingEvent,
    isTypingRef,
    loadInitialChatData,
    mergeScheduledMessages,
    pageSize,
    pendingAttachmentsRef,
    persistedDraftRef,
    resetStructuredComposerState,
    resetTimelineSearchState,
    setChatMessages,
    setComposerSelection,
    setDraft,
    setDraftEntities,
    setError,
    setHasMoreHistory,
    setLoadingHistory,
    setLoadingPinnedHistory,
    setMembers,
    setPendingAttachments,
    setPinnedHistory,
    setPinnedMessageId,
    setScheduledMessages,
    setSendSilently,
    setShowPinnedHistory,
    setShowScheduledPanel,
    syncScheduledMessages,
    token,
    topicId,
    typingResetRef,
    typingTimeoutsRef
  ]);
}
