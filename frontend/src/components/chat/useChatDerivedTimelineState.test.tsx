import { renderHook } from "@testing-library/react-native";
import type {
  ChatMessage,
  MessageSelectionState,
  PinnedMessageHistoryEntry
} from "../../types";
import { useChatDerivedTimelineState } from "./useChatDerivedTimelineState";

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

describe("useChatDerivedTimelineState", () => {
  it("derives unread, thread, reply, selected, and pin preview state", () => {
    const selectionState: MessageSelectionState = {
      active: true,
      selectedMessageIds: ["reply-1", "unread-1"]
    };
    const threadRootMessage: ChatMessage = {
      ...baseMessage,
      commentCount: 5,
      createdAt: "2026-03-27T10:01:00.000Z",
      discussionChatId: "discussion-1",
      discussionRootMessageId: "discussion-root-1",
      messageId: "thread-root-1",
      senderId: "user-3",
      text: "Thread root"
    };
    const replyMessage: ChatMessage = {
      ...baseMessage,
      createdAt: "2026-03-27T10:01:00.000Z",
      messageId: "reply-1",
      senderId: "user-4",
      text: "Reply target"
    };
    const unreadMessage: ChatMessage = {
      ...baseMessage,
      createdAt: "2026-03-27T10:03:00.000Z",
      messageId: "unread-1",
      replyToMessageId: "reply-1",
      senderId: "user-5",
      text: "Unread message",
      threadRootMessageId: "thread-root-1"
    };
    const pinnedHistoryEntry: PinnedMessageHistoryEntry = {
      active: true,
      chatId: "chat-1",
      message: replyMessage,
      messageId: replyMessage.messageId,
      pinEventId: "pin-1",
      pinnedAt: "2026-03-27T10:04:00.000Z",
      pinnedByDisplayName: "Alice",
      pinnedByUserId: "user-2",
      unpinnedAt: null
    };

    const { result } = renderHook(() =>
      useChatDerivedTimelineState({
        activeThreadRootMessageId: "thread-root-1",
        chatId: "chat-1",
        chatMessages: [threadRootMessage, replyMessage, unreadMessage],
        currentUserId: "user-1",
        lastReadAt: "2026-03-27T10:01:30.000Z",
        messages: [replyMessage, unreadMessage],
        pinnedHistory: [pinnedHistoryEntry],
        pinnedMessageId: null,
        replyToMessageId: "reply-1",
        selectionState,
        unreadCount: 2
      })
    );

    expect(result.current.threadRootMessage?.messageId).toBe("thread-root-1");
    expect(result.current.activeDiscussionChatId).toBe("discussion-1");
    expect(result.current.activeDiscussionRootMessageId).toBe("discussion-root-1");
    expect(result.current.replyTarget?.messageId).toBe("reply-1");
    expect(result.current.selectedMessage?.messageId).toBe("unread-1");
    expect(result.current.selectedMessages.map((message) => message.messageId)).toEqual([
      "reply-1",
      "unread-1"
    ]);
    expect(result.current.firstUnreadMessage?.messageId).toBe("unread-1");
    expect(result.current.activePinnedHistoryEntry?.messageId).toBe("reply-1");
    expect(result.current.pinnedPreviewMessage?.messageId).toBe("reply-1");
  });
});
