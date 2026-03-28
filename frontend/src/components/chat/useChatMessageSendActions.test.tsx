jest.mock("../../services/api", () => ({
  api: {
    editMessage: jest.fn(),
    scheduleMessage: jest.fn(),
    sendMessage: jest.fn(),
    sendWhenOnlineMessage: jest.fn()
  }
}));

jest.mock("../../services/clientMessageIds", () => ({
  generateClientMessageId: jest.fn(() => "client-message-1")
}));

jest.mock("../../services/localDatabase", () => ({
  localDatabase: {
    upsertScheduledMessages: jest.fn(async () => undefined)
  }
}));

jest.mock("../../services/messageOutbox", () => ({
  messageOutbox: {
    isRetryable: jest.fn(),
    queueMessage: jest.fn()
  }
}));

jest.mock("../../services/scheduledMessageOutbox", () => ({
  scheduledMessageOutbox: {
    isRetryable: jest.fn(),
    queueMessage: jest.fn()
  }
}));

jest.mock("./useChatMediaComposer", () => ({
  PendingAttachmentUploadError: class PendingAttachmentUploadError extends Error {
    attachments;

    cause;

    constructor(message: string, attachments: unknown[], cause: unknown) {
      super(message);
      this.attachments = attachments;
      this.cause = cause;
    }
  }
}));

import { act, renderHook } from "@testing-library/react-native";
import { api } from "../../services/api";
import { messageOutbox } from "../../services/messageOutbox";
import { scheduledMessageOutbox } from "../../services/scheduledMessageOutbox";
import type { ChatMessage, MessageAttachment, ScheduledMessage } from "../../types";
import { PendingAttachmentUploadError } from "./useChatMediaComposer";
import { useChatMessageSendActions } from "./useChatMessageSendActions";

function createPendingAttachment(
  overrides: Partial<MessageAttachment> = {}
): MessageAttachment {
  return {
    accessExpiresAt: null,
    attachmentId: "attachment-local-1",
    contentType: "image/jpeg",
    downloadUrl: "",
    durationMs: null,
    fileSizeBytes: 1024,
    height: 900,
    kind: "IMAGE",
    localUri: "file://queued/photo.jpg",
    originalFileName: "photo.jpg",
    previewUrl: "file://queued/photo.jpg",
    requiresAuthorization: false,
    streamingSupported: false,
    thumbnailUrl: "file://queued/photo.jpg",
    uploadState: "PENDING_UPLOAD",
    waveform: null,
    width: 1200,
    ...overrides
  };
}

function createQueuedMessage(
  overrides: Partial<ChatMessage> = {}
): ChatMessage {
  return {
    anonymousSender: false,
    attachments: [],
    caption: null,
    chatId: "chat-1",
    clientMessageId: "client-message-1",
    commentCount: 0,
    contactCard: null,
    createdAt: "2026-03-28T10:00:00.000Z",
    deletedAt: null,
    deliveredAt: null,
    deliveryStatus: "QUEUED",
    discussionChatId: null,
    discussionRootMessageId: null,
    displaySenderName: "Alex",
    displaySenderPhotoAccessExpiresAt: null,
    displaySenderPhotoUrl: null,
    editedAt: null,
    entities: [],
    expiresAt: null,
    forwardedFromChatId: null,
    forwardedFromMessageId: null,
    liveLocation: null,
    location: null,
    messageId: "queued:client-message-1",
    messageType: "IMAGE",
    poll: null,
    reactions: [],
    readAt: null,
    recipientId: null,
    replyToMessageId: null,
    senderId: "user-1",
    serviceMessage: null,
    silent: false,
    sticker: null,
    text: "Hello",
    threadRootMessageId: null,
    topicId: null,
    viaBotUserId: null,
    ...overrides
  };
}

function createQueuedScheduledMessage(
  overrides: Partial<ScheduledMessage> = {}
): ScheduledMessage {
  return {
    attachments: [],
    caption: null,
    chatId: "chat-1",
    clientMessageId: "client-message-1",
    contactCard: null,
    createdAt: "2026-03-28T10:00:00.000Z",
    discussionChatId: null,
    discussionRootMessageId: null,
    entities: [],
    liveLocation: null,
    location: null,
    messageType: "IMAGE",
    replyToMessageId: null,
    scheduledAt: "2026-03-28T10:10:00.000Z",
    scheduledMessageId: "queued-scheduled:client-message-1",
    senderId: "user-1",
    serviceMessage: null,
    silent: false,
    status: "QUEUED",
    stickerId: null,
    text: "Hello",
    threadRootMessageId: null,
    topicId: null,
    ...overrides
  };
}

function buildHookParams(
  overrides: Partial<Parameters<typeof useChatMessageSendActions>[0]> = {}
): Parameters<typeof useChatMessageSendActions>[0] {
  return {
    activeDiscussionChatId: null,
    activeDiscussionRootMessageId: null,
    activeStructuredMessageType: null,
    activeThreadRootMessageId: null,
    appendScheduledMessage: jest.fn(),
    canPost: true,
    canSendContact: false,
    canSendLiveLocation: false,
    canSendLocation: false,
    chatId: "chat-1",
    chatType: "DIRECT",
    currentUserId: "user-1",
    editingMessageId: null,
    effectiveReplyToMessageId: null,
    normalizedComposerDraft: {
      entities: [],
      text: "Hello"
    },
    optimisticAuthor: {
      anonymousSender: false,
      displaySenderName: "Alex",
      displaySenderPhotoAccessExpiresAt: null,
      displaySenderPhotoUrl: null
    },
    parsedLiveLocation: null,
    parsedLocation: null,
    pendingAttachments: [createPendingAttachment()],
    persistMessage: jest.fn(),
    preparedContactCard: null,
    recordingVoice: false,
    resetComposerState: jest.fn(),
    resolvePendingAttachmentsForSend: jest.fn(async (attachments: MessageAttachment[]) => attachments),
    scheduling: false,
    sendSilently: false,
    sending: false,
    setError: jest.fn(),
    setScheduling: jest.fn(),
    setSending: jest.fn(),
    setShowScheduledPanel: jest.fn(),
    showPollComposer: false,
    syncQueuedMessage: jest.fn(),
    token: "token-1",
    topicId: null,
    touchMyLastSentAt: jest.fn(),
    uploadingAttachments: false,
    ...overrides
  };
}

function createRetryableUploadError(attachments: MessageAttachment[]) {
  return new PendingAttachmentUploadError(
    "Unable to upload queued attachment",
    attachments,
    new Error("network timeout")
  );
}

describe("useChatMessageSendActions", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (messageOutbox.isRetryable as jest.Mock).mockReturnValue(true);
    (scheduledMessageOutbox.isRetryable as jest.Mock).mockReturnValue(true);
  });

  it("queues send flow instead of partially sending when attachment upload is retryable", async () => {
    const pendingAttachment = createPendingAttachment();
    const queuedMessage = createQueuedMessage({
      attachments: [pendingAttachment]
    });
    const setError = jest.fn();
    const setSending = jest.fn();
    const resetComposerState = jest.fn();
    const syncQueuedMessage = jest.fn();

    (messageOutbox.queueMessage as jest.Mock).mockResolvedValue(queuedMessage);

    const { result } = renderHook(() =>
      useChatMessageSendActions(
        buildHookParams({
          pendingAttachments: [pendingAttachment],
          resetComposerState,
          resolvePendingAttachmentsForSend: jest.fn(async () => {
            throw createRetryableUploadError([pendingAttachment]);
          }),
          setError,
          setSending,
          syncQueuedMessage
        })
      )
    );

    await act(async () => {
      await result.current.handleSend();
    });

    expect(api.sendMessage).not.toHaveBeenCalled();
    expect(messageOutbox.queueMessage).toHaveBeenCalledWith(
      expect.objectContaining({
        attachments: [pendingAttachment],
        chatId: "chat-1",
        currentUserId: "user-1",
        operation: {
          kind: "SEND_MESSAGE",
          request: expect.objectContaining({
            attachmentIds: [],
            chatId: "chat-1",
            clientMessageId: "client-message-1",
            text: "Hello"
          })
        },
        optimistic: expect.objectContaining({
          attachments: [pendingAttachment],
          text: "Hello"
        })
      })
    );
    expect(syncQueuedMessage).toHaveBeenCalledWith(queuedMessage);
    expect(resetComposerState).toHaveBeenCalledTimes(1);
    expect(setError).toHaveBeenLastCalledWith(
      "Attachment upload will resume after sync. Message queued."
    );
    expect(setSending).toHaveBeenNthCalledWith(1, true);
    expect(setSending).toHaveBeenLastCalledWith(false);
  });

  it("queues scheduled flow instead of calling schedule API when attachment upload is retryable", async () => {
    const pendingAttachment = createPendingAttachment();
    const queuedMessage = createQueuedScheduledMessage({
      attachments: [pendingAttachment]
    });
    const appendScheduledMessage = jest.fn();
    const setError = jest.fn();
    const setScheduling = jest.fn();
    const setShowScheduledPanel = jest.fn();
    const resetComposerState = jest.fn();

    (scheduledMessageOutbox.queueMessage as jest.Mock).mockResolvedValue(queuedMessage);

    const { result } = renderHook(() =>
      useChatMessageSendActions(
        buildHookParams({
          appendScheduledMessage,
          pendingAttachments: [pendingAttachment],
          resetComposerState,
          resolvePendingAttachmentsForSend: jest.fn(async () => {
            throw createRetryableUploadError([pendingAttachment]);
          }),
          setError,
          setScheduling,
          setShowScheduledPanel
        })
      )
    );

    await act(async () => {
      await result.current.handleScheduleMessage();
    });

    expect(api.scheduleMessage).not.toHaveBeenCalled();
    expect(scheduledMessageOutbox.queueMessage).toHaveBeenCalledWith(
      expect.objectContaining({
        attachments: [pendingAttachment],
        chatId: "chat-1",
        currentUserId: "user-1",
        mode: "SCHEDULED",
        payload: expect.objectContaining({
          attachmentIds: [],
          chatId: "chat-1",
          clientMessageId: "client-message-1",
          scheduledAt: expect.any(String),
          text: "Hello"
        })
      })
    );
    expect(appendScheduledMessage).toHaveBeenCalledWith(queuedMessage);
    expect(resetComposerState).toHaveBeenCalledTimes(1);
    expect(setShowScheduledPanel).toHaveBeenCalledWith(true);
    expect(setError).toHaveBeenLastCalledWith(
      "Attachment upload will resume after sync. Scheduled message queued."
    );
    expect(setScheduling).toHaveBeenNthCalledWith(1, true);
    expect(setScheduling).toHaveBeenLastCalledWith(false);
  });

  it("queues send-when-online flow instead of calling API when attachment upload is retryable", async () => {
    const pendingAttachment = createPendingAttachment();
    const queuedMessage = createQueuedScheduledMessage({
      attachments: [pendingAttachment]
    });
    const appendScheduledMessage = jest.fn();
    const setError = jest.fn();
    const setScheduling = jest.fn();
    const setShowScheduledPanel = jest.fn();
    const resetComposerState = jest.fn();

    (scheduledMessageOutbox.queueMessage as jest.Mock).mockResolvedValue(queuedMessage);

    const { result } = renderHook(() =>
      useChatMessageSendActions(
        buildHookParams({
          appendScheduledMessage,
          pendingAttachments: [pendingAttachment],
          resetComposerState,
          resolvePendingAttachmentsForSend: jest.fn(async () => {
            throw createRetryableUploadError([pendingAttachment]);
          }),
          setError,
          setScheduling,
          setShowScheduledPanel
        })
      )
    );

    await act(async () => {
      await result.current.handleSendWhenOnline();
    });

    expect(api.sendWhenOnlineMessage).not.toHaveBeenCalled();
    expect(scheduledMessageOutbox.queueMessage).toHaveBeenCalledWith(
      expect.objectContaining({
        attachments: [pendingAttachment],
        chatId: "chat-1",
        currentUserId: "user-1",
        mode: "WHEN_ONLINE",
        payload: expect.objectContaining({
          attachmentIds: [],
          chatId: "chat-1",
          clientMessageId: "client-message-1",
          text: "Hello"
        })
      })
    );
    expect(appendScheduledMessage).toHaveBeenCalledWith(queuedMessage);
    expect(resetComposerState).toHaveBeenCalledTimes(1);
    expect(setShowScheduledPanel).toHaveBeenCalledWith(true);
    expect(setError).toHaveBeenLastCalledWith(
      "Attachment upload will resume after sync. Message will wait for the recipient after sync."
    );
    expect(setScheduling).toHaveBeenNthCalledWith(1, true);
    expect(setScheduling).toHaveBeenLastCalledWith(false);
  });
});
