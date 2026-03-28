import { useCallback } from "react";
import { Share } from "react-native";
import { api } from "../../services/api";
import { generateClientMessageId } from "../../services/clientMessageIds";
import { deviceLocation } from "../../services/deviceLocation";
import { messageOutbox } from "../../services/messageOutbox";
import type { MessageComposerSelection } from "../../services/messageFormatting";
import type { ChatMessage, MessageTextEntity, PinMessageEvent } from "../../types";

type OptimisticAuthor = {
  anonymousSender: boolean;
  displaySenderName: string | null;
  displaySenderPhotoAccessExpiresAt: string | null;
  displaySenderPhotoUrl: string | null;
};

type UseChatSelectionActionsParams = {
  activeDiscussionChatId: string | null;
  activeDiscussionRootMessageId: string | null;
  activeThreadRootMessageId: string | null;
  canPinMessages: boolean;
  canPost: boolean;
  chatId: string;
  currentUserId: string;
  describeMessage: (message: ChatMessage) => string;
  effectiveReplyToMessageId: string | null;
  optimisticAuthor: OptimisticAuthor;
  persistMessage: (message: ChatMessage) => void;
  reactionsEnabled: boolean;
  reactingMessageId: string | null;
  resetStructuredMessageInputs: () => void;
  selectedMessage: ChatMessage | null;
  selectedMessages: ChatMessage[];
  sending: boolean;
  setComposerSelection: React.Dispatch<React.SetStateAction<MessageComposerSelection>>;
  setDraft: React.Dispatch<React.SetStateAction<string>>;
  setDraftEntities: React.Dispatch<React.SetStateAction<MessageTextEntity[]>>;
  setEditingMessageId: React.Dispatch<React.SetStateAction<string | null>>;
  setError: (value: string | null) => void;
  setReactingMessageId: React.Dispatch<React.SetStateAction<string | null>>;
  setReplyToMessageId: React.Dispatch<React.SetStateAction<string | null>>;
  setSelectedMessageId: React.Dispatch<React.SetStateAction<string | null>>;
  setSending: React.Dispatch<React.SetStateAction<boolean>>;
  syncQueuedMessage: (message: ChatMessage) => void;
  token: string;
  topicId: string | null;
  touchMyLastSentAt: (sentAt: string) => void;
  onPinEvent: (event: PinMessageEvent) => void;
};

function buildSharedMessageTranscript(
  messages: ChatMessage[],
  describeMessage: (message: ChatMessage) => string
) {
  return messages
    .slice()
    .sort((left, right) => left.createdAt.localeCompare(right.createdAt))
    .map((message, index) => {
      const preview =
        message.text ||
        message.serviceMessage?.text ||
        (message.deletedAt ? "Message deleted" : describeMessage(message));

      return `${index + 1}. ${new Date(message.createdAt).toLocaleString()}
${preview}`;
    })
    .join("\n\n");
}

export function useChatSelectionActions({
  activeDiscussionChatId,
  activeDiscussionRootMessageId,
  activeThreadRootMessageId,
  canPinMessages,
  canPost,
  chatId,
  currentUserId,
  describeMessage,
  effectiveReplyToMessageId,
  optimisticAuthor,
  persistMessage,
  reactionsEnabled,
  reactingMessageId,
  resetStructuredMessageInputs,
  selectedMessage,
  selectedMessages,
  sending,
  setComposerSelection,
  setDraft,
  setDraftEntities,
  setEditingMessageId,
  setError,
  setReactingMessageId,
  setReplyToMessageId,
  setSelectedMessageId,
  setSending,
  syncQueuedMessage,
  token,
  topicId,
  touchMyLastSentAt,
  onPinEvent
}: UseChatSelectionActionsParams) {
  const clearSelection = useCallback(() => {
    setSelectedMessageId(null);
  }, [setSelectedMessageId]);

  const handleDeleteSelected = useCallback(async () => {
    const deletableMessages = selectedMessages.filter(
      (message) => message.senderId === currentUserId && !message.deletedAt
    );
    if (deletableMessages.length === 0 || sending) {
      return;
    }
    setSending(true);
    setError(null);
    try {
      for (const message of deletableMessages) {
        persistMessage(await api.deleteMessage(token, message.messageId));
      }
      setDraft("");
      setEditingMessageId(null);
      clearSelection();
    } catch (deleteError) {
      setError(deleteError instanceof Error ? deleteError.message : "Unable to delete message");
    } finally {
      setSending(false);
    }
  }, [
    clearSelection,
    currentUserId,
    persistMessage,
    selectedMessages,
    sending,
    setDraft,
    setEditingMessageId,
    setError,
    setSending,
    token
  ]);

  const handleForwardSelected = useCallback(async () => {
    const forwardableMessages = selectedMessages
      .filter((message) => !message.deletedAt && message.deliveryStatus !== "QUEUED")
      .sort((left, right) => left.createdAt.localeCompare(right.createdAt));

    if (forwardableMessages.length === 0 || sending || !canPost) {
      return;
    }

    setSending(true);
    setError(null);
    try {
      let queuedForwards = 0;

      for (const sourceMessage of forwardableMessages) {
        const payload = {
          sourceMessageId: sourceMessage.messageId,
          chatId,
          topicId: topicId ?? undefined,
          replyToMessageId: effectiveReplyToMessageId ?? undefined,
          clientMessageId: generateClientMessageId()
        };

        try {
          const message = await api.forwardMessage(token, payload);
          persistMessage(message);
          touchMyLastSentAt(message.createdAt);
        } catch (forwardError) {
          if (!messageOutbox.isRetryable(forwardError)) {
            throw forwardError;
          }

          const queuedMessage = await messageOutbox.queueMessage({
            chatId,
            currentUserId,
            operation: {
              kind: "FORWARD_MESSAGE",
              request: payload
            },
            attachments: [],
            optimistic: {
              ...optimisticAuthor,
              text: sourceMessage.text,
              entities: sourceMessage.entities,
              replyToMessageId: effectiveReplyToMessageId ?? null,
              threadRootMessageId: activeThreadRootMessageId,
              discussionChatId: activeDiscussionChatId,
              discussionRootMessageId: activeDiscussionRootMessageId,
              forwardedFromChatId: sourceMessage.chatId,
              forwardedFromMessageId: sourceMessage.messageId,
              poll: sourceMessage.poll,
              sticker: sourceMessage.sticker,
              attachments: sourceMessage.attachments
            }
          });
          syncQueuedMessage(queuedMessage);
          queuedForwards += 1;
        }
      }

      clearSelection();
      if (queuedForwards > 0) {
        setError(
          queuedForwards === forwardableMessages.length
            ? "No connection. Forward queued."
            : `${queuedForwards} forwarded message(s) queued after connection loss.`
        );
      }
    } catch (forwardError) {
      setError(forwardError instanceof Error ? forwardError.message : "Unable to forward message");
    } finally {
      setSending(false);
    }
  }, [
    activeDiscussionChatId,
    activeDiscussionRootMessageId,
    activeThreadRootMessageId,
    canPost,
    currentUserId,
    effectiveReplyToMessageId,
    optimisticAuthor,
    persistMessage,
    selectedMessages,
    sending,
    clearSelection,
    setError,
    setSending,
    syncQueuedMessage,
    token,
    topicId,
    touchMyLastSentAt
  ]);

  const handleShareSelected = useCallback(async () => {
    if (selectedMessages.length === 0) {
      return;
    }

    setError(null);
    try {
      await Share.share({
        message: buildSharedMessageTranscript(selectedMessages, describeMessage)
      });
      clearSelection();
    } catch (shareError) {
      setError(shareError instanceof Error ? shareError.message : "Unable to share selected messages");
    }
  }, [clearSelection, describeMessage, selectedMessages, setError]);

  const handlePinSelected = useCallback(async () => {
    if (selectedMessages.length !== 1 || !selectedMessage || !canPinMessages) {
      return;
    }
    setError(null);
    try {
      onPinEvent(await api.pinMessage(token, chatId, selectedMessage.messageId));
      clearSelection();
    } catch (pinError) {
      setError(pinError instanceof Error ? pinError.message : "Unable to pin message");
    }
  }, [
    canPinMessages,
    chatId,
    clearSelection,
    onPinEvent,
    selectedMessage,
    selectedMessages.length,
    setError,
    token
  ]);

  const handleToggleReaction = useCallback(async (
    emoji: string,
    message: ChatMessage | null = selectedMessage
  ) => {
    if (!message || message.deletedAt || reactingMessageId) {
      return;
    }
    if (!reactionsEnabled) {
      setError("Reactions are disabled for this chat");
      return;
    }
    setReactingMessageId(message.messageId);
    setError(null);
    try {
      const updated = await api.toggleReaction(token, message.messageId, emoji);
      persistMessage(updated);
    } catch (reactionError) {
      setError(reactionError instanceof Error ? reactionError.message : "Unable to toggle reaction");
    } finally {
      setReactingMessageId(null);
    }
  }, [
    persistMessage,
    reactingMessageId,
    reactionsEnabled,
    selectedMessage,
    setError,
    setReactingMessageId,
    token
  ]);

  const handleReportSelected = useCallback(async () => {
    if (
      selectedMessages.length !== 1 ||
      !selectedMessage ||
      selectedMessage.deletedAt ||
      selectedMessage.deliveryStatus === "QUEUED" ||
      selectedMessage.senderId === currentUserId
    ) {
      return;
    }
    setError(null);
    try {
      await api.reportMessage(token, selectedMessage.messageId, {
        category: "ABUSE"
      });
      clearSelection();
    } catch (reportError) {
      setError(reportError instanceof Error ? reportError.message : "Unable to report message");
    }
  }, [clearSelection, currentUserId, selectedMessage, selectedMessages.length, setError, token]);

  const handleRefreshLiveLocation = useCallback(async () => {
    if (
      selectedMessages.length !== 1 ||
      !selectedMessage?.liveLocation ||
      selectedMessage.senderId !== currentUserId ||
      selectedMessage.deletedAt ||
      sending
    ) {
      return;
    }
    setSending(true);
    setError(null);
    try {
      const snapshot = await deviceLocation.getCurrentPosition();
      const updated = await api.updateLiveLocation(token, selectedMessage.messageId, {
        latitude: snapshot.latitude,
        longitude: snapshot.longitude,
        title: selectedMessage.liveLocation.title,
        address: snapshot.address ?? selectedMessage.liveLocation.address
      });
      persistMessage(updated);
      clearSelection();
    } catch (updateError) {
      setError(updateError instanceof Error ? updateError.message : "Unable to refresh live location");
    } finally {
      setSending(false);
    }
  }, [
    clearSelection,
    currentUserId,
    persistMessage,
    selectedMessage,
    selectedMessages.length,
    sending,
    setError,
    setSending,
    token
  ]);

  const handleStopLiveLocation = useCallback(async () => {
    if (
      selectedMessages.length !== 1 ||
      !selectedMessage?.liveLocation ||
      selectedMessage.senderId !== currentUserId ||
      selectedMessage.deletedAt ||
      sending
    ) {
      return;
    }
    setSending(true);
    setError(null);
    try {
      const updated = await api.stopLiveLocation(token, selectedMessage.messageId);
      persistMessage(updated);
      clearSelection();
    } catch (stopError) {
      setError(stopError instanceof Error ? stopError.message : "Unable to stop live location");
    } finally {
      setSending(false);
    }
  }, [
    clearSelection,
    currentUserId,
    persistMessage,
    selectedMessage,
    selectedMessages.length,
    sending,
    setError,
    setSending,
    token
  ]);

  const beginEditSelected = useCallback(() => {
    if (
      selectedMessages.length !== 1 ||
      !selectedMessage ||
      selectedMessage.senderId !== currentUserId ||
      selectedMessage.deletedAt
    ) {
      return;
    }
    resetStructuredMessageInputs();
    setEditingMessageId(selectedMessage.messageId);
    setDraft(selectedMessage.text);
    setDraftEntities(selectedMessage.entities);
    setComposerSelection({
      start: selectedMessage.text.length,
      end: selectedMessage.text.length
    });
    clearSelection();
  }, [
    clearSelection,
    currentUserId,
    resetStructuredMessageInputs,
    selectedMessage,
    selectedMessages.length,
    setComposerSelection,
    setDraft,
    setDraftEntities,
    setEditingMessageId
  ]);

  const beginReplySelected = useCallback(() => {
    if (selectedMessages.length !== 1 || !selectedMessage) {
      return;
    }
    resetStructuredMessageInputs();
    setReplyToMessageId(selectedMessage.messageId);
    setEditingMessageId(null);
    setDraftEntities([]);
    setComposerSelection({ start: 0, end: 0 });
    clearSelection();
  }, [
    clearSelection,
    resetStructuredMessageInputs,
    selectedMessage,
    selectedMessages.length,
    setComposerSelection,
    setDraftEntities,
    setEditingMessageId,
    setReplyToMessageId,
  ]);

  const cancelComposerModes = useCallback(() => {
    setEditingMessageId(null);
    setReplyToMessageId(null);
    clearSelection();
    setDraft("");
    setDraftEntities([]);
    setComposerSelection({ start: 0, end: 0 });
    resetStructuredMessageInputs();
  }, [
    clearSelection,
    resetStructuredMessageInputs,
    setComposerSelection,
    setDraft,
    setDraftEntities,
    setEditingMessageId,
    setReplyToMessageId
  ]);

  return {
    beginEditSelected,
    beginReplySelected,
    cancelComposerModes,
    handleDeleteSelected,
    handleForwardSelected,
    handlePinSelected,
    handleRefreshLiveLocation,
    handleReportSelected,
    handleShareSelected,
    handleStopLiveLocation,
    handleToggleReaction
  };
}
