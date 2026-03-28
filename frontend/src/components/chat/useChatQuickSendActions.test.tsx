jest.mock("../../services/api", () => ({
  api: {
    createPollMessage: jest.fn(),
    sendInlineBotResult: jest.fn(),
    sendMessage: jest.fn()
  }
}));

jest.mock("../../services/clientMessageIds", () => ({
  generateClientMessageId: jest.fn(() => "client-message-1")
}));

jest.mock("../../services/messageOutbox", () => ({
  messageOutbox: {
    isRetryable: jest.fn(),
    queueMessage: jest.fn()
  }
}));

import { act, renderHook } from "@testing-library/react-native";
import { api } from "../../services/api";
import { messageOutbox } from "../../services/messageOutbox";
import type { ChatMessage, InlineBotResult, StickerPack } from "../../types";
import { useChatQuickSendActions } from "./useChatQuickSendActions";

function createMessage(overrides: Partial<ChatMessage> = {}): ChatMessage {
  return {
    anonymousSender: false,
    attachments: [],
    caption: null,
    chatId: "chat-1",
    clientMessageId: "client-message-1",
    commentCount: 0,
    contactCard: null,
    createdAt: "2026-03-28T12:00:00.000Z",
    deletedAt: null,
    deliveredAt: null,
    deliveryStatus: "SENT",
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
    messageId: "message-1",
    messageType: "TEXT",
    poll: null,
    reactions: [],
    readAt: null,
    recipientId: null,
    replyToMessageId: null,
    senderId: "user-1",
    serviceMessage: null,
    silent: false,
    sticker: null,
    text: "",
    threadRootMessageId: null,
    topicId: null,
    viaBotUserId: null,
    ...overrides
  };
}

function createStickerPacks(): StickerPack[] {
  return [
    {
      packId: "pack-1",
      slug: "default",
      title: "Default pack",
      stickers: [
        {
          backgroundFrom: "#000000",
          backgroundTo: "#ffffff",
          emoji: "😀",
          label: "Smile",
          packId: "pack-1",
          packTitle: "Default pack",
          stickerId: "sticker-1",
          textColor: "#ffffff"
        }
      ]
    }
  ];
}

function createInlineResult(): InlineBotResult {
  return {
    botUserId: "bot-1",
    botUsername: "gifbot",
    description: "Inline result",
    resultId: "result-1",
    text: "Inline result text",
    title: "Result title"
  };
}

function buildHookParams(
  overrides: Partial<Parameters<typeof useChatQuickSendActions>[0]> = {}
): Parameters<typeof useChatQuickSendActions>[0] {
  return {
    activeDiscussionChatId: null,
    activeDiscussionRootMessageId: null,
    activeInlineQuery: {
      query: "kitten"
    },
    activeThreadRootMessageId: null,
    canPost: true,
    chatId: "chat-1",
    closeRichMediaPickers: jest.fn(),
    currentUserId: "user-1",
    editingMessageId: null,
    effectiveReplyToMessageId: "reply-1",
    optimisticAuthor: {
      anonymousSender: false,
      displaySenderName: "Alex",
      displaySenderPhotoAccessExpiresAt: null,
      displaySenderPhotoUrl: null
    },
    persistMessage: jest.fn(),
    pollMultipleChoice: false,
    pollOptions: ["One", "Two"],
    pollQuestion: "Which option?",
    recordingVoice: false,
    resetComposerState: jest.fn(),
    resetPollComposer: jest.fn(),
    sendSilently: false,
    sending: false,
    setError: jest.fn(),
    setReplyToMessageId: jest.fn(),
    setSelectedMessageId: jest.fn(),
    setSending: jest.fn(),
    showPollComposer: false,
    stickerPacks: createStickerPacks(),
    syncQueuedMessage: jest.fn(),
    token: "token-1",
    topicId: null,
    touchMyLastSentAt: jest.fn(),
    uploadingAttachments: false,
    ...overrides
  };
}

describe("useChatQuickSendActions", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (messageOutbox.isRetryable as jest.Mock).mockReturnValue(true);
  });

  it("clears reply state after a poll is sent successfully", async () => {
    const persistMessage = jest.fn();
    const resetPollComposer = jest.fn();
    const setReplyToMessageId = jest.fn();
    const setSelectedMessageId = jest.fn();
    const touchMyLastSentAt = jest.fn();
    const sentMessage = createMessage({
      messageId: "poll-1",
      messageType: "POLL",
      poll: {
        closed: false,
        multipleChoice: false,
        options: [],
        pollId: "poll-1",
        question: "Which option?",
        totalVoters: 0
      },
      text: "Which option?"
    });

    (api.createPollMessage as jest.Mock).mockResolvedValue(sentMessage);

    const { result } = renderHook(() =>
      useChatQuickSendActions(
        buildHookParams({
          persistMessage,
          resetPollComposer,
          setReplyToMessageId,
          setSelectedMessageId,
          touchMyLastSentAt
        })
      )
    );

    await act(async () => {
      await result.current.handleCreatePoll();
    });

    expect(api.createPollMessage).toHaveBeenCalledWith(
      "token-1",
      expect.objectContaining({
        chatId: "chat-1",
        clientMessageId: "client-message-1",
        question: "Which option?",
        replyToMessageId: "reply-1"
      })
    );
    expect(persistMessage).toHaveBeenCalledWith(sentMessage);
    expect(touchMyLastSentAt).toHaveBeenCalledWith(sentMessage.createdAt);
    expect(setReplyToMessageId).toHaveBeenCalledWith(null);
    expect(resetPollComposer).toHaveBeenCalledTimes(1);
    expect(setSelectedMessageId).toHaveBeenCalledWith(null);
  });

  it("queues polls offline and still clears reply state", async () => {
    const queuedMessage = createMessage({
      deliveryStatus: "QUEUED",
      messageId: "queued-poll-1",
      messageType: "POLL",
      poll: {
        closed: false,
        multipleChoice: false,
        options: [],
        pollId: "queued-poll-1",
        question: "Which option?",
        totalVoters: 0
      },
      text: "Which option?"
    });
    const resetPollComposer = jest.fn();
    const setError = jest.fn();
    const setReplyToMessageId = jest.fn();
    const setSelectedMessageId = jest.fn();
    const syncQueuedMessage = jest.fn();

    (api.createPollMessage as jest.Mock).mockRejectedValue(new Error("network timeout"));
    (messageOutbox.queueMessage as jest.Mock).mockResolvedValue(queuedMessage);

    const { result } = renderHook(() =>
      useChatQuickSendActions(
        buildHookParams({
          resetPollComposer,
          setError,
          setReplyToMessageId,
          setSelectedMessageId,
          syncQueuedMessage
        })
      )
    );

    await act(async () => {
      await result.current.handleCreatePoll();
    });

    expect(messageOutbox.queueMessage).toHaveBeenCalledWith(
      expect.objectContaining({
        attachments: [],
        chatId: "chat-1",
        currentUserId: "user-1",
        operation: {
          kind: "CREATE_POLL_MESSAGE",
          request: expect.objectContaining({
            chatId: "chat-1",
            clientMessageId: "client-message-1",
            question: "Which option?",
            replyToMessageId: "reply-1"
          })
        }
      })
    );
    expect(syncQueuedMessage).toHaveBeenCalledWith(queuedMessage);
    expect(setReplyToMessageId).toHaveBeenCalledWith(null);
    expect(resetPollComposer).toHaveBeenCalledTimes(1);
    expect(setSelectedMessageId).toHaveBeenCalledWith(null);
    expect(setError).toHaveBeenLastCalledWith("No connection. Poll queued.");
  });

  it("queues stickers offline and keeps quick-send cleanup consistent", async () => {
    const queuedMessage = createMessage({
      deliveryStatus: "QUEUED",
      messageId: "queued-sticker-1",
      messageType: "TEXT"
    });
    const closeRichMediaPickers = jest.fn();
    const setError = jest.fn();
    const setReplyToMessageId = jest.fn();
    const setSelectedMessageId = jest.fn();
    const syncQueuedMessage = jest.fn();

    (api.sendMessage as jest.Mock).mockRejectedValue(new Error("network timeout"));
    (messageOutbox.queueMessage as jest.Mock).mockResolvedValue(queuedMessage);

    const { result } = renderHook(() =>
      useChatQuickSendActions(
        buildHookParams({
          closeRichMediaPickers,
          setError,
          setReplyToMessageId,
          setSelectedMessageId,
          syncQueuedMessage
        })
      )
    );

    await act(async () => {
      await result.current.handleSendSticker("sticker-1");
    });

    expect(api.sendMessage).toHaveBeenCalledWith(
      "token-1",
      expect.objectContaining({
        chatId: "chat-1",
        clientMessageId: "client-message-1",
        replyToMessageId: "reply-1",
        stickerId: "sticker-1"
      })
    );
    expect(messageOutbox.queueMessage).toHaveBeenCalledWith(
      expect.objectContaining({
        operation: {
          kind: "SEND_MESSAGE",
          request: expect.objectContaining({
            stickerId: "sticker-1"
          })
        },
        optimistic: expect.objectContaining({
          sticker: expect.objectContaining({
            stickerId: "sticker-1"
          })
        })
      })
    );
    expect(syncQueuedMessage).toHaveBeenCalledWith(queuedMessage);
    expect(setReplyToMessageId).toHaveBeenCalledWith(null);
    expect(setSelectedMessageId).toHaveBeenCalledWith(null);
    expect(closeRichMediaPickers).toHaveBeenCalledTimes(1);
    expect(setError).toHaveBeenLastCalledWith("No connection. Sticker queued.");
  });

  it("queues inline bot results offline and resets the composer", async () => {
    const queuedMessage = createMessage({
      deliveryStatus: "QUEUED",
      messageId: "queued-inline-1",
      text: "Inline result text",
      viaBotUserId: "bot-1"
    });
    const resetComposerState = jest.fn();
    const setError = jest.fn();
    const syncQueuedMessage = jest.fn();
    const inlineResult = createInlineResult();

    (api.sendInlineBotResult as jest.Mock).mockRejectedValue(new Error("network timeout"));
    (messageOutbox.queueMessage as jest.Mock).mockResolvedValue(queuedMessage);

    const { result } = renderHook(() =>
      useChatQuickSendActions(
        buildHookParams({
          resetComposerState,
          setError,
          syncQueuedMessage
        })
      )
    );

    await act(async () => {
      await result.current.handleSendInlineResult(inlineResult);
    });

    expect(api.sendInlineBotResult).toHaveBeenCalledWith(
      "token-1",
      expect.objectContaining({
        botUsername: "gifbot",
        chatId: "chat-1",
        clientMessageId: "client-message-1",
        query: "kitten",
        replyToMessageId: "reply-1",
        resultId: "result-1"
      })
    );
    expect(messageOutbox.queueMessage).toHaveBeenCalledWith(
      expect.objectContaining({
        operation: {
          kind: "SEND_INLINE_BOT_RESULT",
          request: expect.objectContaining({
            botUsername: "gifbot",
            resultId: "result-1"
          })
        },
        optimistic: expect.objectContaining({
          text: "Inline result text",
          viaBotUserId: "bot-1"
        })
      })
    );
    expect(syncQueuedMessage).toHaveBeenCalledWith(queuedMessage);
    expect(resetComposerState).toHaveBeenCalledTimes(1);
    expect(setError).toHaveBeenLastCalledWith("No connection. Inline result queued.");
  });
});
