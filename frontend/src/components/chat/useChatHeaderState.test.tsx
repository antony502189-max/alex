import { renderHook } from "@testing-library/react-native";
import type { ChatMessage, ChatSummary, ForumTopic } from "../../types";
import { useChatHeaderState } from "./useChatHeaderState";

const baseChat: ChatSummary = {
  about: "General chat",
  archived: true,
  autoDeleteSeconds: 3600,
  chatId: "chat-1",
  chatType: "DIRECT",
  commentsEnabled: true,
  crossPostingEnabled: true,
  draftText: null,
  draftUpdatedAt: null,
  forumEnabled: false,
  joinRequiresApproval: false,
  lastMessageAt: "2026-03-27T10:00:00.000Z",
  lastReadAt: null,
  markedUnread: false,
  memberCount: 12,
  mentionCount: 0,
  mutedUntil: new Date(Date.now() + 60_000).toISOString(),
  peerBotSupportsInline: true,
  peerBotWebAppUrl: null,
  peerDisplayName: "Alice",
  peerIsBot: false,
  peerLastSeenAt: null,
  peerOnline: true,
  peerPhoneNumber: "+123456",
  peerUserId: "user-2",
  photoAccessExpiresAt: null,
  photoUrl: null,
  pinOrder: null,
  pinned: false,
  pinnedMessageId: null,
  publicUsername: "alice",
  reactionsEnabled: true,
  replyCount: 0,
  slowModeSeconds: null,
  title: "Alice",
  topicCount: 0,
  unreadCount: 0,
  linkedDiscussionChatId: null,
  linkedDiscussionChatTitle: null
};

const pinnedMessage: ChatMessage = {
  anonymousSender: false,
  attachments: [],
  caption: null,
  chatId: "chat-1",
  clientMessageId: null,
  commentCount: 5,
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
  senderId: "user-1",
  serviceMessage: null,
  silent: false,
  sticker: null,
  text: "Pinned hello",
  threadRootMessageId: null,
  topicId: null,
  viaBotUserId: null
};

describe("useChatHeaderState", () => {
  it("composes header and panel labels from chat presentation inputs", () => {
    const { result } = renderHook(() =>
      useChatHeaderState({
        activeThreadRootMessageId: null,
        chat: baseChat,
        describeMessage: () => "Pinned hello",
        editingMessageId: "msg-1",
        loadingPinnedHistory: false,
        memberCount: 12,
        pinnedHistoryLength: 3,
        pinnedPreviewMessage: pinnedMessage,
        showPinnedHistory: false,
        threadCommentCount: null,
        threadTitle: null,
        topic: null
      })
    );

    expect(result.current.headerPresentation.title).toBe("Alice");
    expect(result.current.headerPresentation.subtitle).toContain("online");
    expect(result.current.headerPresentation.subtitle).toContain("@alice");
    expect(result.current.muteToggleLabel).toBe("Unmute");
    expect(result.current.archiveToggleLabel).toBe("Unarchive");
    expect(result.current.pinnedHistoryToggleLabel).toBe("Pins (3)");
    expect(result.current.pinnedPreviewText).toBe("Pinned hello");
    expect(result.current.replyPanelTitle).toBe("Editing message");
    expect(result.current.scheduledPanelTitle).toBe("Scheduled messages");

    const topic: ForumTopic = {
      chatId: "chat-1",
      closed: false,
      createdAt: "2026-03-27T10:00:00.000Z",
      createdBy: "user-1",
      generalTopic: false,
      hidden: false,
      iconEmoji: "#",
      lastMessageAt: null,
      title: "Announcements",
      topicId: "topic-1",
      updatedAt: "2026-03-27T10:00:00.000Z"
    };
    const topicResult = renderHook(() =>
      useChatHeaderState({
        activeThreadRootMessageId: null,
        chat: { ...baseChat, chatType: "GROUP", title: "Team" },
        describeMessage: () => "Pinned hello",
        editingMessageId: null,
        loadingPinnedHistory: false,
        memberCount: 24,
        pinnedHistoryLength: 0,
        pinnedPreviewMessage: null,
        showPinnedHistory: false,
        threadCommentCount: null,
        threadTitle: null,
        topic
      })
    );

    expect(topicResult.result.current.headerPresentation.title).toBe("# Announcements");
    expect(topicResult.result.current.scheduledPanelTitle).toBe("Scheduled in Announcements");
  });
});
