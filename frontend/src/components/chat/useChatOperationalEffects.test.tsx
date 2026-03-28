import { act, renderHook, waitFor } from "@testing-library/react-native";
import { api } from "../../services/api";
import type {
  ChatMember,
  ChatMessage,
  ChatReadEvent,
  ChatSummary
} from "../../types";
import { useChatOperationalEffects } from "./useChatOperationalEffects";

jest.mock("../../services/api", () => ({
  api: {
    clearDraft: jest.fn(),
    markRead: jest.fn(),
    saveDraft: jest.fn(),
    sendTyping: jest.fn()
  }
}));

const mockedApi = api as jest.Mocked<typeof api>;

const baseMessage: ChatMessage = {
  anonymousSender: false,
  attachments: [],
  caption: null,
  chatId: "chat-1",
  clientMessageId: null,
  commentCount: 0,
  contactCard: null,
  createdAt: "2026-03-27T10:00:00.000Z",
  deletedAt: null,
  deliveredAt: null,
  deliveryStatus: "DELIVERED",
  discussionChatId: null,
  discussionRootMessageId: null,
  displaySenderName: null,
  displaySenderPhotoAccessExpiresAt: null,
  displaySenderPhotoUrl: null,
  editedAt: null,
  entities: [],
  expiresAt: null,
  forwardedFromChatId: null,
  forwardedFromMessageId: null,
  location: null,
  messageId: "msg-1",
  messageType: "TEXT",
  poll: null,
  readAt: null,
  reactions: [],
  recipientId: null,
  replyToMessageId: null,
  senderId: "user-2",
  serviceMessage: null,
  silent: false,
  sticker: null,
  text: "Hello",
  threadRootMessageId: null,
  topicId: null,
  viaBotUserId: null
};

const baseMember: ChatMember = {
  anonymousAdmin: false,
  canApproveJoinRequests: false,
  canManageInviteLinks: false,
  canManageMembers: false,
  canManageMessages: false,
  canPinMessages: false,
  canPostMessages: false,
  canSendMessages: true,
  displayName: "Alice",
  joinedAt: "2026-03-01T00:00:00.000Z",
  lastReadAt: "2026-03-27T10:02:00.000Z",
  lastSentMessageAt: null,
  phoneNumber: null,
  photoAccessExpiresAt: null,
  photoUrl: null,
  restrictedUntil: null,
  restrictionReason: null,
  role: "member",
  userId: "user-2"
};

describe("useChatOperationalEffects", () => {
  beforeEach(() => {
    jest.useFakeTimers();
    mockedApi.clearDraft.mockReset();
    mockedApi.markRead.mockReset();
    mockedApi.saveDraft.mockReset();
    mockedApi.sendTyping.mockReset();
  });

  afterEach(() => {
    jest.runOnlyPendingTimers();
    jest.useRealTimers();
  });

  it("syncs draft, marks peer messages as read, and sends typing updates", async () => {
    const readEvent: ChatReadEvent = {
      chatId: "chat-1",
      messageId: "msg-1",
      readAt: "2026-03-27T10:03:00.000Z",
      userId: "user-1"
    };

    mockedApi.saveDraft.mockResolvedValue({
      draftText: "hello"
    } as ChatSummary);
    mockedApi.markRead.mockResolvedValue(readEvent);
    mockedApi.sendTyping.mockResolvedValue({
      chatId: "chat-1",
      emittedAt: "2026-03-27T10:03:00.000Z",
      typing: true,
      userId: "user-1"
    });

    const handleReadEvent = jest.fn();
    const onRefreshChats = jest.fn().mockResolvedValue(undefined);
    const persistedDraftRef = { current: "" };
    const isTypingRef = { current: false };
    const typingResetRef = { current: null as ReturnType<typeof setTimeout> | null };
    const setCurrentTimeMs = jest.fn();
    const setError = jest.fn();
    const upsertChat = jest.fn();

    const { result } = renderHook(() =>
      useChatOperationalEffects({
        canPost: true,
        chatId: "chat-1",
        currentUserId: "user-1",
        draft: "hello",
        editingMessageId: null,
        handleReadEvent,
        isTypingRef,
        members: [baseMember],
        messages: [baseMessage],
        onRefreshChats,
        persistedDraftRef,
        setCurrentTimeMs,
        setError,
        slowModeEndsAt: null,
        token: "token-1",
        typingResetRef,
        typingUserIds: ["user-2"],
        upsertChat
      })
    );

    expect(result.current.typingLabel).toBe("Alice typing...");
    expect(mockedApi.sendTyping).toHaveBeenCalledWith("token-1", "chat-1", true);

    await waitFor(() =>
      expect(mockedApi.markRead).toHaveBeenCalledWith("token-1", "chat-1", "msg-1")
    );
    await waitFor(() => expect(handleReadEvent).toHaveBeenCalledWith(readEvent));
    await waitFor(() => expect(onRefreshChats).toHaveBeenCalled());

    act(() => {
      jest.advanceTimersByTime(450);
    });

    await waitFor(() =>
      expect(mockedApi.saveDraft).toHaveBeenCalledWith("token-1", "chat-1", "hello")
    );
    await waitFor(() => expect(upsertChat).toHaveBeenCalled());
    expect(persistedDraftRef.current).toBe("hello");

    act(() => {
      jest.advanceTimersByTime(1200);
    });

    await waitFor(() =>
      expect(mockedApi.sendTyping).toHaveBeenLastCalledWith("token-1", "chat-1", false)
    );
    expect(isTypingRef.current).toBe(false);
    expect(setError).not.toHaveBeenCalled();
    expect(setCurrentTimeMs).not.toHaveBeenCalled();
  });

  it("ticks slow mode timers and skips read receipts for local messages", () => {
    mockedApi.clearDraft.mockResolvedValue({ draftText: null } as ChatSummary);
    mockedApi.sendTyping.mockResolvedValue({
      chatId: "chat-1",
      emittedAt: "2026-03-27T10:03:00.000Z",
      typing: false,
      userId: "user-1"
    });

    const setCurrentTimeMs = jest.fn();

    renderHook(() =>
      useChatOperationalEffects({
        canPost: false,
        chatId: "chat-1",
        currentUserId: "user-1",
        draft: "",
        editingMessageId: null,
        handleReadEvent: jest.fn(),
        isTypingRef: { current: false },
        members: [],
        messages: [{ ...baseMessage, senderId: "user-1" }],
        onRefreshChats: undefined,
        persistedDraftRef: { current: "" },
        setCurrentTimeMs,
        setError: jest.fn(),
        slowModeEndsAt: Date.now() + 10_000,
        token: "token-1",
        typingResetRef: { current: null },
        typingUserIds: [],
        upsertChat: jest.fn()
      })
    );

    act(() => {
      jest.advanceTimersByTime(1000);
    });

    expect(setCurrentTimeMs).toHaveBeenCalled();
    expect(mockedApi.markRead).not.toHaveBeenCalled();
  });
});
