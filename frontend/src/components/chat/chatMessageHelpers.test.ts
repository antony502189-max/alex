import type { ChatMember, ChatMessage, MessageAttachment, ScheduledMessage } from "../../types";
import {
  describeMessage,
  formatAutoDelete,
  formatContactName,
  formatFileSize,
  formatLocationSummary,
  renderMessageMeta,
  resolveDisplaySenderName,
  seenCount
} from "./chatMessageHelpers";

const baseAttachment: MessageAttachment = {
  accessExpiresAt: null,
  attachmentId: "att-1",
  contentType: "image/jpeg",
  downloadUrl: "",
  durationMs: null,
  fileSizeBytes: 2048,
  height: 480,
  kind: "IMAGE",
  originalFileName: "photo.jpg",
  previewUrl: null,
  requiresAuthorization: false,
  streamingSupported: false,
  thumbnailUrl: null,
  uploadState: "UPLOADED",
  waveform: null,
  width: 640
};

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
  senderId: "user-1",
  serviceMessage: null,
  silent: false,
  sticker: null,
  text: "Hello",
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
  silent: false,
  status: "PENDING",
  stickerId: null,
  text: "",
  threadRootMessageId: null,
  topicId: null
};

const members: ChatMember[] = [
  {
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
  }
];

describe("chatMessageHelpers", () => {
  it("formats location and contact summaries", () => {
    expect(
      formatLocationSummary({
        address: "Main street",
        latitude: 53.9,
        longitude: 27.5667,
        title: "Office"
      })
    ).toBe("Office - Main street");
    expect(formatContactName({ firstName: "Jane", lastName: "Doe", phoneNumber: null, userId: null, vcard: null })).toBe(
      "Jane Doe"
    );
  });

  it("describes attachments and stickers", () => {
    const attachmentMessage: ScheduledMessage = {
      ...baseScheduledMessage,
      attachments: [baseAttachment]
    };
    const stickerMessage: ScheduledMessage = {
      ...baseScheduledMessage,
      stickerId: "sticker-1"
    };

    expect(describeMessage(attachmentMessage, () => "Photo")).toBe("Photo");
    expect(describeMessage(stickerMessage, () => "Photo")).toBe("Sticker");
  });

  it("resolves sender names for self and members", () => {
    expect(resolveDisplaySenderName({ ...baseMessage, senderId: "user-1" }, "user-1", members)).toBe("You");
    expect(resolveDisplaySenderName({ ...baseMessage, senderId: "user-2" }, "user-1", members)).toBe("Alice");
  });

  it("counts reads and renders message meta flags", () => {
    const message = {
      ...baseMessage,
      anonymousSender: true,
      editedAt: "2026-03-27T10:01:00.000Z",
      expiresAt: "2026-03-27T11:00:00.000Z",
      forwardedFromMessageId: "forward-1",
      silent: true
    };

    expect(seenCount(message, "user-1", members)).toBe(1);

    const meta = renderMessageMeta(message, "user-1", members);
    expect(meta).toContain("forwarded");
    expect(meta).toContain("silent");
    expect(meta).toContain("anonymous admin");
    expect(meta).toContain("delivered");
    expect(meta).toContain("edited");
    expect(meta).toContain("expires");
    expect(meta).toContain("seen by 1");
  });

  it("formats sizes and auto-delete labels", () => {
    expect(formatFileSize(900)).toBe("900 B");
    expect(formatFileSize(2048)).toBe("2.0 KB");
    expect(formatFileSize(2 * 1024 * 1024)).toBe("2.0 MB");
    expect(formatAutoDelete(45)).toBe("auto-delete 45s");
    expect(formatAutoDelete(180)).toBe("auto-delete 3m");
    expect(formatAutoDelete(7200)).toBe("auto-delete 2h");
    expect(formatAutoDelete(172800)).toBe("auto-delete 2d");
  });
});
