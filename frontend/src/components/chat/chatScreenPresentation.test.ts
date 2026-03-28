import type { ChatMessage, ChatSummary, ForumTopic, PinnedMessageHistoryEntry, ScheduledMessage } from "../../types";
import {
  buildChatHeaderPresentation,
  formatPinnedHistoryEntryMeta,
  formatPinnedHistoryEntryPreview,
  formatScheduledMessageMeta,
  getArchiveToggleLabel,
  getMuteToggleLabel,
  getPinnedHistoryToggleLabel,
  getPinnedPreviewText,
  getReplyPanelTitle,
  getScheduledPanelTitle
} from "./chatScreenPresentation";

const baseChat: ChatSummary = {
  about: "General chat",
  archived: false,
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
  mutedUntil: null,
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

const baseMessage: ChatMessage = {
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

const baseScheduledMessage: ScheduledMessage = {
  attachments: [],
  caption: null,
  chatId: "chat-1",
  clientMessageId: null,
  contactCard: null,
  createdAt: "2026-03-27T10:00:00.000Z",
  discussionChatId: null,
  discussionRootMessageId: null,
  entities: [],
  location: null,
  messageType: "TEXT",
  replyToMessageId: null,
  scheduledAt: "2026-03-27T10:10:00.000Z",
  scheduledMessageId: "scheduled-1",
  senderId: "user-1",
  serviceMessage: null,
  silent: true,
  status: "WAITING_ONLINE",
  stickerId: null,
  text: "",
  threadRootMessageId: null,
  topicId: null
};

describe("chatScreenPresentation", () => {
  it("builds direct and thread header presentations", () => {
    const direct = buildChatHeaderPresentation({
      activeThreadRootMessageId: null,
      chat: baseChat,
      directPresenceLabel: "online now",
      memberCount: 12,
      threadCommentCount: null,
      threadTitle: null,
      topic: null
    });

    expect(direct.title).toBe("Alice");
    expect(direct.subtitle).toContain("online now");
    expect(direct.subtitle).toContain("@alice");
    expect(direct.subtitle).toContain("auto-delete 1h");

    const thread = buildChatHeaderPresentation({
      activeThreadRootMessageId: "msg-1",
      chat: { ...baseChat, chatType: "GROUP", title: "Team" },
      directPresenceLabel: "",
      memberCount: 12,
      threadCommentCount: 5,
      threadTitle: "Sprint thread",
      topic: null
    });

    expect(thread.title).toBe("Sprint thread");
    expect(thread.subtitle).toContain("Team");
    expect(thread.subtitle).toContain("5 comments");
  });

  it("formats pinned, toggle, and reply labels", () => {
    expect(getMuteToggleLabel(new Date(Date.now() + 60000).toISOString(), Date.now())).toBe("Unmute");
    expect(getArchiveToggleLabel(true)).toBe("Unarchive");
    expect(getPinnedHistoryToggleLabel(false, 3)).toBe("Pins (3)");
    expect(getReplyPanelTitle("msg-1", null)).toBe("Editing message");

    expect(
      getPinnedPreviewText({
        describeMessage: () => "Pinned hello",
        loadingPinnedHistory: false,
        pinnedPreviewMessage: baseMessage
      })
    ).toBe("Pinned hello");
  });

  it("formats pinned history and scheduled descriptors", () => {
    const entry: PinnedMessageHistoryEntry = {
      active: false,
      chatId: "chat-1",
      message: baseMessage,
      messageId: baseMessage.messageId,
      pinEventId: "pin-1",
      pinnedAt: "2026-03-27T10:20:00.000Z",
      pinnedByDisplayName: "Alice",
      pinnedByUserId: "user-2",
      unpinnedAt: "2026-03-27T10:30:00.000Z"
    };

    expect(formatPinnedHistoryEntryPreview(entry, () => "Pinned hello")).toBe("Pinned hello");
    expect(formatPinnedHistoryEntryMeta(entry)).toContain("by Alice");
    expect(
      getScheduledPanelTitle({
        activeThreadRootMessageId: "msg-1",
        threadTitle: "Sprint thread",
        topic: null
      })
    ).toBe("Scheduled in Sprint thread");
    expect(formatScheduledMessageMeta(baseScheduledMessage)).toContain("when recipient is online");
    expect(formatScheduledMessageMeta(baseScheduledMessage)).toContain("silent");
  });

  it("builds topic header titles", () => {
    const topic: ForumTopic = {
      chatId: "chat-1",
      closed: true,
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

    const result = buildChatHeaderPresentation({
      activeThreadRootMessageId: null,
      chat: { ...baseChat, chatType: "GROUP", title: "Team" },
      directPresenceLabel: "",
      memberCount: 24,
      threadCommentCount: null,
      threadTitle: null,
      topic
    });

    expect(result.title).toBe("# Announcements");
    expect(result.subtitle).toContain("closed topic");
  });

  it("uses subscriber wording for channel header subtitles", () => {
    const result = buildChatHeaderPresentation({
      activeThreadRootMessageId: null,
      chat: { ...baseChat, chatType: "CHANNEL", publicUsername: "news", title: "News" },
      directPresenceLabel: "",
      memberCount: 1200,
      threadCommentCount: null,
      threadTitle: null,
      topic: null
    });

    expect(result.subtitle).toContain("1200 subscribers");
  });
});
