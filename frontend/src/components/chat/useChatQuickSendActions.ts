import { useCallback } from "react";
import { api } from "../../services/api";
import { generateClientMessageId } from "../../services/clientMessageIds";
import { messageOutbox } from "../../services/messageOutbox";
import type { ChatMessage, InlineBotResult, StickerPack } from "../../types";

type OptimisticAuthor = {
  anonymousSender: boolean;
  displaySenderName: string | null;
  displaySenderPhotoAccessExpiresAt: string | null;
  displaySenderPhotoUrl: string | null;
};

type UseChatQuickSendActionsParams = {
  activeDiscussionChatId: string | null;
  activeDiscussionRootMessageId: string | null;
  activeInlineQuery: { query: string } | null;
  activeThreadRootMessageId: string | null;
  canPost: boolean;
  chatId: string;
  closeRichMediaPickers: () => void;
  currentUserId: string;
  editingMessageId: string | null;
  effectiveReplyToMessageId: string | null;
  optimisticAuthor: OptimisticAuthor;
  persistMessage: (message: ChatMessage) => void;
  pollMultipleChoice: boolean;
  pollOptions: string[];
  pollQuestion: string;
  recordingVoice: boolean;
  resetComposerState: () => void;
  resetPollComposer: () => void;
  sendSilently: boolean;
  sending: boolean;
  setError: (value: string | null) => void;
  setReplyToMessageId: React.Dispatch<React.SetStateAction<string | null>>;
  setSelectedMessageId: React.Dispatch<React.SetStateAction<string | null>>;
  setSending: React.Dispatch<React.SetStateAction<boolean>>;
  showPollComposer: boolean;
  stickerPacks: StickerPack[];
  syncQueuedMessage: (message: ChatMessage) => void;
  token: string;
  topicId: string | null;
  touchMyLastSentAt: (sentAt: string) => void;
  uploadingAttachments: boolean;
};

export function useChatQuickSendActions({
  activeDiscussionChatId,
  activeDiscussionRootMessageId,
  activeInlineQuery,
  activeThreadRootMessageId,
  canPost,
  chatId,
  closeRichMediaPickers,
  currentUserId,
  editingMessageId,
  effectiveReplyToMessageId,
  optimisticAuthor,
  persistMessage,
  pollMultipleChoice,
  pollOptions,
  pollQuestion,
  recordingVoice,
  resetComposerState,
  resetPollComposer,
  sendSilently,
  sending,
  setError,
  setReplyToMessageId,
  setSelectedMessageId,
  setSending,
  showPollComposer,
  stickerPacks,
  syncQueuedMessage,
  token,
  topicId,
  touchMyLastSentAt,
  uploadingAttachments
}: UseChatQuickSendActionsParams) {
  const handleCreatePoll = useCallback(async () => {
    const normalizedQuestion = pollQuestion.trim();
    const normalizedOptions = pollOptions
      .map((option) => option.trim())
      .filter(Boolean);

    if (!normalizedQuestion || normalizedOptions.length < 2 || sending || !canPost) {
      return;
    }

    setSending(true);
    setError(null);
    const clientMessageId = generateClientMessageId();
    const payload = {
      chatId,
      topicId: topicId ?? undefined,
      replyToMessageId: effectiveReplyToMessageId ?? undefined,
      question: normalizedQuestion,
      options: normalizedOptions,
      multipleChoice: pollMultipleChoice,
      clientMessageId
    };
    try {
      const message = await api.createPollMessage(token, payload);
      persistMessage(message);
      touchMyLastSentAt(message.createdAt);
      setReplyToMessageId(null);
      resetPollComposer();
      setSelectedMessageId(null);
    } catch (pollError) {
      if (messageOutbox.isRetryable(pollError)) {
        try {
          const queuedMessage = await messageOutbox.queueMessage({
            chatId,
            currentUserId,
            operation: {
              kind: "CREATE_POLL_MESSAGE",
              request: payload
            },
            attachments: [],
            optimistic: {
              ...optimisticAuthor,
              text: normalizedQuestion,
              replyToMessageId: effectiveReplyToMessageId ?? null,
              threadRootMessageId: activeThreadRootMessageId,
              discussionChatId: activeDiscussionChatId,
              discussionRootMessageId: activeDiscussionRootMessageId,
              poll: {
                pollId: `queued-poll:${clientMessageId}`,
                question: normalizedQuestion,
                multipleChoice: pollMultipleChoice,
                closed: false,
                totalVoters: 0,
                options: normalizedOptions.map((option, index) => ({
                  optionId: `queued-poll-option:${clientMessageId}:${index}`,
                  text: option,
                  voteCount: 0,
                  selectedByMe: false
                }))
              }
            }
          });
          syncQueuedMessage(queuedMessage);
          setReplyToMessageId(null);
          resetPollComposer();
          setSelectedMessageId(null);
          setError("No connection. Poll queued.");
        } catch (queueError) {
          setError(queueError instanceof Error ? queueError.message : "Unable to queue poll");
        }
      } else {
        setError(pollError instanceof Error ? pollError.message : "Unable to create poll");
      }
    } finally {
      setSending(false);
    }
  }, [
    activeDiscussionChatId,
    activeDiscussionRootMessageId,
    activeThreadRootMessageId,
    canPost,
    chatId,
    currentUserId,
    effectiveReplyToMessageId,
    optimisticAuthor,
    persistMessage,
    pollMultipleChoice,
    pollOptions,
    pollQuestion,
    resetPollComposer,
    sending,
    setError,
    setReplyToMessageId,
    setSelectedMessageId,
    setSending,
    syncQueuedMessage,
    token,
    topicId,
    touchMyLastSentAt
  ]);

  const handleSendSticker = useCallback(async (stickerId: string) => {
    if (sending || uploadingAttachments || !canPost || editingMessageId || recordingVoice) {
      return;
    }

    setSending(true);
    setError(null);
    const payload = {
      chatId,
      clientMessageId: generateClientMessageId(),
      topicId: topicId ?? undefined,
      replyToMessageId: effectiveReplyToMessageId ?? undefined,
      silent: sendSilently || undefined,
      stickerId
    };
    const sticker =
      stickerPacks
        .flatMap((pack) => pack.stickers)
        .find((item) => item.stickerId === stickerId) ?? null;
    try {
      const message = await api.sendMessage(token, payload);
      persistMessage(message);
      touchMyLastSentAt(message.createdAt);
      setReplyToMessageId(null);
      setSelectedMessageId(null);
      closeRichMediaPickers();
    } catch (sendError) {
      if (messageOutbox.isRetryable(sendError)) {
        try {
          const queuedMessage = await messageOutbox.queueMessage({
            chatId,
            currentUserId,
            operation: {
              kind: "SEND_MESSAGE",
              request: payload
            },
            attachments: [],
            optimistic: {
              ...optimisticAuthor,
              silent: sendSilently,
              replyToMessageId: effectiveReplyToMessageId ?? null,
              threadRootMessageId: activeThreadRootMessageId,
              discussionChatId: activeDiscussionChatId,
              discussionRootMessageId: activeDiscussionRootMessageId,
              sticker
            }
          });
          syncQueuedMessage(queuedMessage);
          setReplyToMessageId(null);
          setSelectedMessageId(null);
          closeRichMediaPickers();
          setError("No connection. Sticker queued.");
        } catch (queueError) {
          setError(queueError instanceof Error ? queueError.message : "Unable to queue sticker");
        }
      } else {
        setError(sendError instanceof Error ? sendError.message : "Unable to send sticker");
      }
    } finally {
      setSending(false);
    }
  }, [
    activeDiscussionChatId,
    activeDiscussionRootMessageId,
    activeThreadRootMessageId,
    canPost,
    chatId,
    closeRichMediaPickers,
    currentUserId,
    editingMessageId,
    effectiveReplyToMessageId,
    optimisticAuthor,
    persistMessage,
    recordingVoice,
    sendSilently,
    sending,
    setError,
    setReplyToMessageId,
    setSelectedMessageId,
    setSending,
    stickerPacks,
    syncQueuedMessage,
    token,
    topicId,
    touchMyLastSentAt,
    uploadingAttachments
  ]);

  const handleSendInlineResult = useCallback(async (result: InlineBotResult) => {
    if (
      sending ||
      uploadingAttachments ||
      !canPost ||
      showPollComposer ||
      editingMessageId ||
      recordingVoice
    ) {
      return;
    }

    setSending(true);
    setError(null);
    const clientMessageId = generateClientMessageId();
    const payload = {
      botUsername: result.botUsername,
      chatId,
      clientMessageId,
      query: activeInlineQuery?.query || "",
      replyToMessageId: effectiveReplyToMessageId ?? undefined,
      resultId: result.resultId,
      topicId: topicId ?? undefined
    };

    try {
      const message = await api.sendInlineBotResult(token, payload);
      persistMessage(message);
      touchMyLastSentAt(message.createdAt);
      resetComposerState();
    } catch (sendError) {
      if (messageOutbox.isRetryable(sendError)) {
        try {
          const queuedMessage = await messageOutbox.queueMessage({
            chatId,
            currentUserId,
            operation: {
              kind: "SEND_INLINE_BOT_RESULT",
              request: payload
            },
            attachments: [],
            optimistic: {
              ...optimisticAuthor,
              text: result.text,
              replyToMessageId: effectiveReplyToMessageId ?? null,
              viaBotUserId: result.botUserId,
              threadRootMessageId: activeThreadRootMessageId,
              discussionChatId: activeDiscussionChatId,
              discussionRootMessageId: activeDiscussionRootMessageId
            }
          });
          syncQueuedMessage(queuedMessage);
          resetComposerState();
          setError("No connection. Inline result queued.");
        } catch (queueError) {
          setError(
            queueError instanceof Error ? queueError.message : "Unable to queue inline result"
          );
        }
      } else {
        setError(sendError instanceof Error ? sendError.message : "Unable to send inline result");
      }
    } finally {
      setSending(false);
    }
  }, [
    activeDiscussionChatId,
    activeDiscussionRootMessageId,
    activeInlineQuery?.query,
    activeThreadRootMessageId,
    canPost,
    chatId,
    currentUserId,
    editingMessageId,
    effectiveReplyToMessageId,
    optimisticAuthor,
    persistMessage,
    recordingVoice,
    resetComposerState,
    sending,
    setError,
    setSending,
    showPollComposer,
    syncQueuedMessage,
    token,
    topicId,
    touchMyLastSentAt,
    uploadingAttachments
  ]);

  return {
    handleCreatePoll,
    handleSendInlineResult,
    handleSendSticker
  };
}
