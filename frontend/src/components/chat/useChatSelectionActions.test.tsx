jest.mock("../../services/api", () => ({
  api: {
    updateLiveLocation: jest.fn()
  }
}));

jest.mock("../../services/deviceLocation", () => ({
  deviceLocation: {
    getCurrentPosition: jest.fn()
  }
}));

import { act, renderHook } from "@testing-library/react-native";
import { Share } from "react-native";
import { api } from "../../services/api";
import { deviceLocation } from "../../services/deviceLocation";
import type { ChatMessage } from "../../types";
import { useChatSelectionActions } from "./useChatSelectionActions";

function createSelectedMessage(overrides: Partial<ChatMessage> = {}): ChatMessage {
  return {
    attachments: [],
    anonymousSender: false,
    caption: null,
    chatId: "chat-1",
    clientMessageId: null,
    commentCount: 0,
    contactCard: null,
    createdAt: "2026-03-27T10:00:00.000Z",
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
    liveLocation: {
      active: true,
      address: "Old address",
      expiresAt: null,
      lastUpdatedAt: null,
      latitude: 53.9,
      livePeriodSeconds: 900,
      longitude: 27.56,
      stoppedAt: null,
      title: "Current location"
    },
    location: null,
    messageId: "message-1",
    messageType: "LIVE_LOCATION",
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

describe("useChatSelectionActions", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (deviceLocation.getCurrentPosition as jest.Mock).mockResolvedValue({
      address: "Minsk, Belarus",
      latitude: 53.90454,
      longitude: 27.56152
    });
    (api.updateLiveLocation as jest.Mock).mockResolvedValue(createSelectedMessage());
  });

  it("refreshes live location using the current device coordinates", async () => {
    const persistMessage = jest.fn();
    const setSelectedMessageId = jest.fn();
    const setSending = jest.fn();
    const setError = jest.fn();
    const selectedMessage = createSelectedMessage();

    const { result } = renderHook(() =>
      useChatSelectionActions({
        activeDiscussionChatId: null,
        activeDiscussionRootMessageId: null,
        activeThreadRootMessageId: null,
        canPinMessages: false,
        canPost: true,
        chatId: "chat-1",
        currentUserId: "user-1",
        describeMessage: () => "Live location",
        effectiveReplyToMessageId: null,
        optimisticAuthor: {
          anonymousSender: false,
          displaySenderName: "Alex",
          displaySenderPhotoAccessExpiresAt: null,
          displaySenderPhotoUrl: null
        },
        onPinEvent: jest.fn(),
        persistMessage,
        reactionsEnabled: true,
        reactingMessageId: null,
        resetStructuredMessageInputs: jest.fn(),
        selectedMessage,
        selectedMessages: [selectedMessage],
        sending: false,
        setComposerSelection: jest.fn(),
        setDraft: jest.fn(),
        setDraftEntities: jest.fn(),
        setEditingMessageId: jest.fn(),
        setError,
        setReactingMessageId: jest.fn(),
        setReplyToMessageId: jest.fn(),
        setSelectedMessageId,
        setSending,
        syncQueuedMessage: jest.fn(),
        token: "token-1",
        topicId: null,
        touchMyLastSentAt: jest.fn()
      })
    );

    await act(async () => {
      await result.current.handleRefreshLiveLocation();
    });

    expect(deviceLocation.getCurrentPosition).toHaveBeenCalledTimes(1);
    expect(api.updateLiveLocation).toHaveBeenCalledWith("token-1", "message-1", {
      address: "Minsk, Belarus",
      latitude: 53.90454,
      longitude: 27.56152,
      title: "Current location"
    });
    expect(persistMessage).toHaveBeenCalledTimes(1);
    expect(setSelectedMessageId).toHaveBeenCalledWith(null);
  });

  it("shares all selected messages as a transcript and clears the selection", async () => {
    const shareSpy = jest.spyOn(Share, "share").mockResolvedValue({
      action: "sharedAction"
    });
    const setSelectedMessageId = jest.fn();
    const firstMessage = createSelectedMessage({
      createdAt: "2026-03-27T10:00:00.000Z",
      liveLocation: null,
      messageId: "message-1",
      messageType: "TEXT",
      text: "First selected message"
    });
    const secondMessage = createSelectedMessage({
      createdAt: "2026-03-27T10:05:00.000Z",
      liveLocation: null,
      messageId: "message-2",
      messageType: "TEXT",
      text: "Second selected message"
    });

    const { result } = renderHook(() =>
      useChatSelectionActions({
        activeDiscussionChatId: null,
        activeDiscussionRootMessageId: null,
        activeThreadRootMessageId: null,
        canPinMessages: false,
        canPost: true,
        chatId: "chat-1",
        currentUserId: "user-1",
        describeMessage: () => "Text message",
        effectiveReplyToMessageId: null,
        optimisticAuthor: {
          anonymousSender: false,
          displaySenderName: "Alex",
          displaySenderPhotoAccessExpiresAt: null,
          displaySenderPhotoUrl: null
        },
        onPinEvent: jest.fn(),
        persistMessage: jest.fn(),
        reactionsEnabled: true,
        reactingMessageId: null,
        resetStructuredMessageInputs: jest.fn(),
        selectedMessage: secondMessage,
        selectedMessages: [firstMessage, secondMessage],
        sending: false,
        setComposerSelection: jest.fn(),
        setDraft: jest.fn(),
        setDraftEntities: jest.fn(),
        setEditingMessageId: jest.fn(),
        setError: jest.fn(),
        setReactingMessageId: jest.fn(),
        setReplyToMessageId: jest.fn(),
        setSelectedMessageId,
        setSending: jest.fn(),
        syncQueuedMessage: jest.fn(),
        token: "token-1",
        topicId: null,
        touchMyLastSentAt: jest.fn()
      })
    );

    await act(async () => {
      await result.current.handleShareSelected();
    });

    expect(shareSpy).toHaveBeenCalledWith({
      message: expect.stringContaining("First selected message")
    });
    expect(shareSpy).toHaveBeenCalledWith({
      message: expect.stringContaining("Second selected message")
    });
    expect(setSelectedMessageId).toHaveBeenCalledWith(null);

    shareSpy.mockRestore();
  });
});
