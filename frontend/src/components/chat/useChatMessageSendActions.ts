import { useCallback } from "react";
import {
  api,
  type DeferredMessagePayload,
  type SendMessagePayload
} from "../../services/api";
import { generateClientMessageId } from "../../services/clientMessageIds";
import { localDatabase } from "../../services/localDatabase";
import { messageOutbox } from "../../services/messageOutbox";
import { scheduledMessageOutbox } from "../../services/scheduledMessageOutbox";
import type {
  ChatMessage,
  ChatSummary,
  MessageAttachment,
  MessageContactCard,
  MessageLiveLocation,
  MessageLocation,
  MessageTextEntity,
  ScheduledMessage
} from "../../types";
import { isQueuedUploadAttachment } from "./chatAttachmentHelpers";
import { PendingAttachmentUploadError } from "./useChatMediaComposer";

type OptimisticAuthor = {
  anonymousSender: boolean;
  displaySenderName: string | null;
  displaySenderPhotoAccessExpiresAt: string | null;
  displaySenderPhotoUrl: string | null;
};

type NormalizedComposerDraft = {
  entities: MessageTextEntity[];
  text: string;
};

type UseChatMessageSendActionsParams = {
  activeDiscussionChatId: string | null;
  activeDiscussionRootMessageId: string | null;
  activeStructuredMessageType: "CONTACT_CARD" | "LOCATION" | "LIVE_LOCATION" | null;
  activeThreadRootMessageId: string | null;
  appendScheduledMessage: (message: ScheduledMessage) => void;
  canPost: boolean;
  canSendContact: boolean;
  canSendLiveLocation: boolean;
  canSendLocation: boolean;
  chatId: string;
  chatType: ChatSummary["chatType"];
  currentUserId: string;
  editingMessageId: string | null;
  effectiveReplyToMessageId: string | null;
  normalizedComposerDraft: NormalizedComposerDraft;
  optimisticAuthor: OptimisticAuthor;
  parsedLiveLocation: MessageLiveLocation | null;
  parsedLocation: MessageLocation | null;
  pendingAttachments: MessageAttachment[];
  preparedContactCard: MessageContactCard | null;
  recordingVoice: boolean;
  resetComposerState: () => void;
  resolvePendingAttachmentsForSend: (attachments: MessageAttachment[]) => Promise<MessageAttachment[]>;
  scheduling: boolean;
  sending: boolean;
  sendSilently: boolean;
  setError: (value: string | null) => void;
  setScheduling: React.Dispatch<React.SetStateAction<boolean>>;
  setSending: React.Dispatch<React.SetStateAction<boolean>>;
  setShowScheduledPanel: React.Dispatch<React.SetStateAction<boolean>>;
  showPollComposer: boolean;
  syncQueuedMessage: (message: ChatMessage) => void;
  token: string;
  topicId: string | null;
  touchMyLastSentAt: (sentAt: string) => void;
  uploadingAttachments: boolean;
  persistMessage: (message: ChatMessage) => void;
};

function buildCaption(
  text: string,
  pendingAttachments: MessageAttachment[],
  activeStructuredMessageType: "CONTACT_CARD" | "LOCATION" | "LIVE_LOCATION" | null
) {
  return pendingAttachments.length > 0 || activeStructuredMessageType ? text || undefined : undefined;
}

function resolveOptimisticMessageType(
  activeStructuredMessageType: "CONTACT_CARD" | "LOCATION" | "LIVE_LOCATION" | null,
  resolvedAttachments: MessageAttachment[]
) {
  if (activeStructuredMessageType) {
    return activeStructuredMessageType;
  }
  if (resolvedAttachments.length === 0) {
    return "TEXT";
  }
  return resolvedAttachments.length > 1 ? "ALBUM" : resolvedAttachments[0].kind;
}

export function useChatMessageSendActions({
  activeDiscussionChatId,
  activeDiscussionRootMessageId,
  activeStructuredMessageType,
  activeThreadRootMessageId,
  appendScheduledMessage,
  canPost,
  canSendContact,
  canSendLiveLocation,
  canSendLocation,
  chatId,
  chatType,
  currentUserId,
  editingMessageId,
  effectiveReplyToMessageId,
  normalizedComposerDraft,
  optimisticAuthor,
  parsedLiveLocation,
  parsedLocation,
  pendingAttachments,
  preparedContactCard,
  recordingVoice,
  resetComposerState,
  resolvePendingAttachmentsForSend,
  scheduling,
  sending,
  sendSilently,
  setError,
  setScheduling,
  setSending,
  setShowScheduledPanel,
  showPollComposer,
  syncQueuedMessage,
  token,
  topicId,
  touchMyLastSentAt,
  uploadingAttachments,
  persistMessage
}: UseChatMessageSendActionsParams) {
  const queueOutgoingMessage = useCallback(async (
    payload: SendMessagePayload,
    resolvedAttachments: MessageAttachment[],
    text: string,
    entities: MessageTextEntity[],
    caption: string | undefined,
    forcedByPendingUpload = false
  ) => {
    const queuedMessage = await messageOutbox.queueMessage({
      chatId,
      currentUserId,
      operation: {
        kind: "SEND_MESSAGE",
        request: payload
      },
      attachments: resolvedAttachments,
      optimistic: {
        ...optimisticAuthor,
        text,
        entities,
        messageType: resolveOptimisticMessageType(
          activeStructuredMessageType,
          resolvedAttachments
        ),
        caption: caption ?? null,
        silent: sendSilently,
        location:
          activeStructuredMessageType === "LOCATION" ? parsedLocation ?? null : null,
        liveLocation:
          activeStructuredMessageType === "LIVE_LOCATION"
            ? parsedLiveLocation ?? null
            : null,
        contactCard:
          activeStructuredMessageType === "CONTACT_CARD"
            ? preparedContactCard ?? null
            : null,
        replyToMessageId: effectiveReplyToMessageId ?? null,
        threadRootMessageId: activeThreadRootMessageId,
        discussionChatId: activeDiscussionChatId,
        discussionRootMessageId: activeDiscussionRootMessageId,
        attachments: resolvedAttachments
      }
    });
    syncQueuedMessage(queuedMessage);
    resetComposerState();
    setError(
      forcedByPendingUpload
        ? "Attachment upload will resume after sync. Message queued."
        : "No connection. Message queued."
    );
  }, [
    activeDiscussionChatId,
    activeDiscussionRootMessageId,
    activeStructuredMessageType,
    activeThreadRootMessageId,
    chatId,
    currentUserId,
    effectiveReplyToMessageId,
    optimisticAuthor,
    parsedLiveLocation,
    parsedLocation,
    preparedContactCard,
    resetComposerState,
    sendSilently,
    setError,
    syncQueuedMessage
  ]);

  const queueDeferredMessage = useCallback(async (
    payload: DeferredMessagePayload,
    resolvedAttachments: MessageAttachment[],
    mode: "SCHEDULED" | "WHEN_ONLINE",
    forcedByPendingUpload = false
  ) => {
    const queuedMessage = await scheduledMessageOutbox.queueMessage({
      chatId,
      currentUserId,
      payload,
      attachments: resolvedAttachments,
      threadRootMessageId: activeThreadRootMessageId,
      discussionChatId: activeDiscussionChatId,
      discussionRootMessageId: activeDiscussionRootMessageId,
      mode
    });
    appendScheduledMessage(queuedMessage);
    resetComposerState();
    setShowScheduledPanel(true);
    setError(
      mode === "WHEN_ONLINE"
        ? forcedByPendingUpload
          ? "Attachment upload will resume after sync. Message will wait for the recipient after sync."
          : "No connection. Message will wait for the recipient after sync."
        : forcedByPendingUpload
          ? "Attachment upload will resume after sync. Scheduled message queued."
          : "No connection. Scheduled message queued."
    );
  }, [
    activeDiscussionChatId,
    activeDiscussionRootMessageId,
    activeThreadRootMessageId,
    appendScheduledMessage,
    chatId,
    currentUserId,
    resetComposerState,
    setError,
    setShowScheduledPanel
  ]);

  const handleSend = useCallback(async () => {
    const { text, entities } = normalizedComposerDraft;
    const caption = buildCaption(text, pendingAttachments, activeStructuredMessageType);
    if (
      (!text &&
        pendingAttachments.length === 0 &&
        !canSendLocation &&
        !canSendLiveLocation &&
        !canSendContact) ||
      sending ||
      uploadingAttachments ||
      !canPost ||
      showPollComposer
    ) {
      return;
    }
    if (activeStructuredMessageType === "LOCATION" && !canSendLocation) {
      setError("Enter valid latitude and longitude");
      return;
    }
    if (activeStructuredMessageType === "LIVE_LOCATION" && !canSendLiveLocation) {
      setError("Enter a valid live location and duration");
      return;
    }
    if (activeStructuredMessageType === "CONTACT_CARD" && !canSendContact) {
      setError("Enter at least a contact name or phone number");
      return;
    }
    setSending(true);
    setError(null);
    let resolvedAttachments = pendingAttachments;
    let forceQueueAfterAttachmentResolution = false;
    try {
      resolvedAttachments = await resolvePendingAttachmentsForSend(pendingAttachments);
    } catch (resolveError) {
      if (resolveError instanceof PendingAttachmentUploadError) {
        if (!messageOutbox.isRetryable(resolveError.cause)) {
          setSending(false);
          setError(
            resolveError.cause instanceof Error
              ? resolveError.cause.message
              : "Unable to upload attachment"
          );
          return;
        }
        resolvedAttachments = resolveError.attachments;
        forceQueueAfterAttachmentResolution = true;
      } else {
        setSending(false);
        setError(resolveError instanceof Error ? resolveError.message : "Unable to prepare attachments");
        return;
      }
    }

    const payload = {
      chatId,
      attachmentIds: resolvedAttachments
        .filter((attachment) => !isQueuedUploadAttachment(attachment))
        .map((item) => item.attachmentId),
      clientMessageId: editingMessageId ? undefined : generateClientMessageId(),
      entities: entities.length > 0 ? entities : undefined,
      topicId: topicId ?? undefined,
      replyToMessageId: effectiveReplyToMessageId ?? undefined,
      text: text || undefined,
      caption,
      silent: sendSilently || undefined,
      messageType: activeStructuredMessageType ?? undefined,
      location: activeStructuredMessageType === "LOCATION" ? parsedLocation ?? undefined : undefined,
      liveLocation:
        activeStructuredMessageType === "LIVE_LOCATION"
          ? parsedLiveLocation ?? undefined
          : undefined,
      contactCard:
        activeStructuredMessageType === "CONTACT_CARD"
          ? preparedContactCard ?? undefined
          : undefined
    };
    if (forceQueueAfterAttachmentResolution && !editingMessageId) {
      try {
        await queueOutgoingMessage(
          payload,
          resolvedAttachments,
          text,
          entities,
          caption,
          true
        );
      } catch (queueError) {
        setError(queueError instanceof Error ? queueError.message : "Unable to queue message");
      } finally {
        setSending(false);
      }
      return;
    }
    try {
      const message = editingMessageId
        ? await api.editMessage(token, editingMessageId, {
            text,
            entities
          })
        : await api.sendMessage(token, payload);
      persistMessage(message);
      if (!editingMessageId) {
        touchMyLastSentAt(message.createdAt);
      }
      resetComposerState();
    } catch (sendError) {
      if (!editingMessageId && messageOutbox.isRetryable(sendError)) {
        try {
          await queueOutgoingMessage(payload, resolvedAttachments, text, entities, caption);
        } catch (queueError) {
          setError(queueError instanceof Error ? queueError.message : "Unable to queue message");
        }
      } else {
        setError(sendError instanceof Error ? sendError.message : "Unable to send message");
      }
    } finally {
      setSending(false);
    }
  }, [
    activeDiscussionChatId,
    activeDiscussionRootMessageId,
    activeStructuredMessageType,
    activeThreadRootMessageId,
    canPost,
    canSendContact,
    canSendLiveLocation,
    canSendLocation,
    chatId,
    currentUserId,
    editingMessageId,
    effectiveReplyToMessageId,
    normalizedComposerDraft,
    optimisticAuthor,
    parsedLiveLocation,
    parsedLocation,
    pendingAttachments,
    persistMessage,
    preparedContactCard,
    resetComposerState,
    resolvePendingAttachmentsForSend,
    sendSilently,
    sending,
    queueOutgoingMessage,
    setError,
    setSending,
    showPollComposer,
    token,
    topicId,
    touchMyLastSentAt,
    uploadingAttachments
  ]);

  const handleScheduleMessage = useCallback(async () => {
    const { text, entities } = normalizedComposerDraft;
    const caption = buildCaption(text, pendingAttachments, activeStructuredMessageType);
    if (
      (!text &&
        pendingAttachments.length === 0 &&
        !canSendLocation &&
        !canSendLiveLocation &&
        !canSendContact) ||
      scheduling ||
      sending ||
      uploadingAttachments ||
      !canPost ||
      showPollComposer ||
      editingMessageId ||
      recordingVoice
    ) {
      return;
    }
    if (activeStructuredMessageType === "LOCATION" && !canSendLocation) {
      setError("Enter valid latitude and longitude");
      return;
    }
    if (activeStructuredMessageType === "LIVE_LOCATION") {
      setError("Live location cannot be scheduled");
      return;
    }
    if (activeStructuredMessageType === "CONTACT_CARD" && !canSendContact) {
      setError("Enter at least a contact name or phone number");
      return;
    }

    setScheduling(true);
    setError(null);
    let resolvedAttachments = pendingAttachments;
    let forceQueueAfterAttachmentResolution = false;
    try {
      resolvedAttachments = await resolvePendingAttachmentsForSend(pendingAttachments);
    } catch (resolveError) {
      if (resolveError instanceof PendingAttachmentUploadError) {
        if (!scheduledMessageOutbox.isRetryable(resolveError.cause)) {
          setScheduling(false);
          setError(
            resolveError.cause instanceof Error
              ? resolveError.cause.message
              : "Unable to upload attachment"
          );
          return;
        }
        resolvedAttachments = resolveError.attachments;
        forceQueueAfterAttachmentResolution = true;
      } else {
        setScheduling(false);
        setError(resolveError instanceof Error ? resolveError.message : "Unable to prepare attachments");
        return;
      }
    }

    const clientMessageId = generateClientMessageId();
    const scheduledAt = new Date(Date.now() + 10 * 60 * 1000).toISOString();
    const payload = {
      chatId,
      attachmentIds: resolvedAttachments
        .filter((attachment) => !isQueuedUploadAttachment(attachment))
        .map((item) => item.attachmentId),
      clientMessageId,
      entities: entities.length > 0 ? entities : undefined,
      topicId: topicId ?? undefined,
      replyToMessageId: effectiveReplyToMessageId ?? undefined,
      stickerId: undefined,
      text: text || undefined,
      caption,
      silent: sendSilently || undefined,
      messageType: activeStructuredMessageType ?? undefined,
      location: activeStructuredMessageType === "LOCATION" ? parsedLocation ?? undefined : undefined,
      contactCard:
        activeStructuredMessageType === "CONTACT_CARD"
          ? preparedContactCard ?? undefined
          : undefined,
      scheduledAt
    };
    if (forceQueueAfterAttachmentResolution) {
      try {
        await queueDeferredMessage(payload, resolvedAttachments, "SCHEDULED", true);
      } catch (queueError) {
        setError(queueError instanceof Error ? queueError.message : "Unable to queue scheduled message");
      } finally {
        setScheduling(false);
      }
      return;
    }
    try {
      const scheduledMessage = await api.scheduleMessage(token, payload);
      appendScheduledMessage(scheduledMessage);
      void localDatabase.upsertScheduledMessages(currentUserId, [scheduledMessage]).catch(() => undefined);
      resetComposerState();
      setShowScheduledPanel(true);
    } catch (scheduleError) {
      if (scheduledMessageOutbox.isRetryable(scheduleError)) {
        try {
          await queueDeferredMessage(payload, resolvedAttachments, "SCHEDULED");
        } catch (queueError) {
          setError(queueError instanceof Error ? queueError.message : "Unable to queue scheduled message");
        }
      } else {
        setError(scheduleError instanceof Error ? scheduleError.message : "Unable to schedule message");
      }
    } finally {
      setScheduling(false);
    }
  }, [
    activeDiscussionChatId,
    activeDiscussionRootMessageId,
    activeStructuredMessageType,
    activeThreadRootMessageId,
    appendScheduledMessage,
    canPost,
    canSendContact,
    canSendLiveLocation,
    canSendLocation,
    chatId,
    currentUserId,
    editingMessageId,
    effectiveReplyToMessageId,
    normalizedComposerDraft,
    parsedLocation,
    pendingAttachments,
    preparedContactCard,
    recordingVoice,
    resetComposerState,
    resolvePendingAttachmentsForSend,
    scheduling,
    sendSilently,
    sending,
    queueDeferredMessage,
    setError,
    setScheduling,
    setShowScheduledPanel,
    showPollComposer,
    token,
    topicId,
    uploadingAttachments
  ]);

  const handleSendWhenOnline = useCallback(async () => {
    const { text, entities } = normalizedComposerDraft;
    const caption = buildCaption(text, pendingAttachments, activeStructuredMessageType);
    if (
      chatType !== "DIRECT" ||
      (!text &&
        pendingAttachments.length === 0 &&
        !canSendLocation &&
        !canSendLiveLocation &&
        !canSendContact) ||
      scheduling ||
      sending ||
      uploadingAttachments ||
      !canPost ||
      showPollComposer ||
      editingMessageId ||
      recordingVoice
    ) {
      return;
    }
    if (activeStructuredMessageType === "LOCATION" && !canSendLocation) {
      setError("Enter valid latitude and longitude");
      return;
    }
    if (activeStructuredMessageType === "LIVE_LOCATION") {
      setError("Live location cannot be queued for online delivery");
      return;
    }
    if (activeStructuredMessageType === "CONTACT_CARD" && !canSendContact) {
      setError("Enter at least a contact name or phone number");
      return;
    }

    setScheduling(true);
    setError(null);
    let resolvedAttachments = pendingAttachments;
    let forceQueueAfterAttachmentResolution = false;
    try {
      resolvedAttachments = await resolvePendingAttachmentsForSend(pendingAttachments);
    } catch (resolveError) {
      if (resolveError instanceof PendingAttachmentUploadError) {
        if (!scheduledMessageOutbox.isRetryable(resolveError.cause)) {
          setScheduling(false);
          setError(
            resolveError.cause instanceof Error
              ? resolveError.cause.message
              : "Unable to upload attachment"
          );
          return;
        }
        resolvedAttachments = resolveError.attachments;
        forceQueueAfterAttachmentResolution = true;
      } else {
        setScheduling(false);
        setError(resolveError instanceof Error ? resolveError.message : "Unable to prepare attachments");
        return;
      }
    }

    const clientMessageId = generateClientMessageId();
    const payload = {
      chatId,
      attachmentIds: resolvedAttachments
        .filter((attachment) => !isQueuedUploadAttachment(attachment))
        .map((item) => item.attachmentId),
      clientMessageId,
      entities: entities.length > 0 ? entities : undefined,
      topicId: topicId ?? undefined,
      replyToMessageId: effectiveReplyToMessageId ?? undefined,
      text: text || undefined,
      caption,
      silent: sendSilently || undefined,
      messageType: activeStructuredMessageType ?? undefined,
      location: activeStructuredMessageType === "LOCATION" ? parsedLocation ?? undefined : undefined,
      contactCard:
        activeStructuredMessageType === "CONTACT_CARD"
          ? preparedContactCard ?? undefined
          : undefined
    };
    if (forceQueueAfterAttachmentResolution) {
      try {
        await queueDeferredMessage(payload, resolvedAttachments, "WHEN_ONLINE", true);
      } catch (queueError) {
        setError(queueError instanceof Error ? queueError.message : "Unable to queue online delivery");
      } finally {
        setScheduling(false);
      }
      return;
    }

    try {
      const deferredMessage = await api.sendWhenOnlineMessage(token, payload);
      appendScheduledMessage(deferredMessage);
      void localDatabase.upsertScheduledMessages(currentUserId, [deferredMessage]).catch(() => undefined);
      resetComposerState();
      setShowScheduledPanel(true);
    } catch (sendError) {
      if (scheduledMessageOutbox.isRetryable(sendError)) {
        try {
          await queueDeferredMessage(payload, resolvedAttachments, "WHEN_ONLINE");
        } catch (queueError) {
          setError(queueError instanceof Error ? queueError.message : "Unable to queue online delivery");
        }
      } else {
        setError(sendError instanceof Error ? sendError.message : "Unable to queue online delivery");
      }
    } finally {
      setScheduling(false);
    }
  }, [
    activeDiscussionChatId,
    activeDiscussionRootMessageId,
    activeStructuredMessageType,
    activeThreadRootMessageId,
    appendScheduledMessage,
    canPost,
    canSendContact,
    canSendLiveLocation,
    canSendLocation,
    chatId,
    chatType,
    currentUserId,
    editingMessageId,
    effectiveReplyToMessageId,
    normalizedComposerDraft,
    parsedLocation,
    pendingAttachments,
    preparedContactCard,
    recordingVoice,
    resetComposerState,
    resolvePendingAttachmentsForSend,
    scheduling,
    sendSilently,
    sending,
    queueDeferredMessage,
    setError,
    setScheduling,
    setShowScheduledPanel,
    showPollComposer,
    token,
    topicId,
    uploadingAttachments
  ]);

  return {
    handleScheduleMessage,
    handleSend,
    handleSendWhenOnline
  };
}
