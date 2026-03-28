import type {
  ChatMessage,
  MessageAttachment
} from "../../types";
import {
  attachmentLabel,
  isAudioAttachment,
  buildSharedMediaBuckets,
  buildSharedMediaCountLine,
  buildSharedMediaUpdatedLine,
  formatFileSize,
  isMediaAttachment,
  normalizeSharedMediaLinkUrl
} from "./sharedMediaPresentation";

function createAttachment(overrides: Partial<MessageAttachment> = {}): MessageAttachment {
  return {
    attachmentId: "attachment-1",
    originalFileName: "photo.jpg",
    contentType: "image/jpeg",
    kind: "IMAGE",
    fileSizeBytes: 2048,
    durationMs: null,
    downloadUrl: "/attachments/1",
    previewUrl: "https://cdn.example/preview.jpg",
    thumbnailUrl: "https://cdn.example/thumb.jpg",
    width: 800,
    height: 600,
    waveform: null,
    accessExpiresAt: null,
    requiresAuthorization: true,
    streamingSupported: false,
    ...overrides
  };
}

function createMessage(overrides: Partial<ChatMessage> = {}): ChatMessage {
  return {
    chatId: "chat-1",
    messageId: "message-1",
    clientMessageId: null,
    senderId: "user-1",
    displaySenderName: "Alex",
    displaySenderPhotoUrl: null,
    displaySenderPhotoAccessExpiresAt: null,
    anonymousSender: false,
    recipientId: null,
    viaBotUserId: null,
    topicId: null,
    threadRootMessageId: null,
    discussionChatId: null,
    discussionRootMessageId: null,
    commentCount: 0,
    text: "Look at https://example.com",
    entities: [],
    messageType: "TEXT",
    caption: null,
    silent: false,
    location: null,
    contactCard: null,
    serviceMessage: null,
    createdAt: "2026-03-27T12:00:00.000Z",
    replyToMessageId: null,
    forwardedFromChatId: null,
    forwardedFromMessageId: null,
    poll: null,
    sticker: null,
    attachments: [],
    reactions: [],
    deliveryStatus: "SENT",
    deliveredAt: null,
    readAt: null,
    expiresAt: null,
    editedAt: null,
    deletedAt: null,
    ...overrides
  };
}

describe("sharedMediaPresentation", () => {
  it("builds media, file, and link buckets from recent messages", () => {
    const imageAttachment = createAttachment();
    const fileAttachment = createAttachment({
      attachmentId: "attachment-2",
      originalFileName: "notes.pdf",
      contentType: "application/pdf",
      kind: "FILE",
      previewUrl: null,
      thumbnailUrl: null
    });

    const buckets = buildSharedMediaBuckets("chat-1", [
      createMessage({
        messageId: "message-1",
        attachments: [fileAttachment],
        text: "Document alex://join/team and tg://call/room-77",
        createdAt: "2026-03-27T11:00:00.000Z"
      }),
      createMessage({
        messageId: "message-2",
        attachments: [imageAttachment],
        text: "Look at https://example.com and t.me/team",
        createdAt: "2026-03-27T12:00:00.000Z"
      }),
      createMessage({
        messageId: "message-3",
        deletedAt: "2026-03-27T12:10:00.000Z",
        attachments: [createAttachment({ attachmentId: "attachment-3" })]
      })
    ]);

    expect(buckets.media).toHaveLength(1);
    expect(buckets.files).toHaveLength(1);
    expect(buckets.links).toHaveLength(4);
    expect(buckets.media[0]?.messageId).toBe("message-2");
    expect(buildSharedMediaCountLine(buckets)).toBe("1 media - 1 files - 4 links");
    expect(buildSharedMediaUpdatedLine(buckets)).toContain("Updated ");
  });

  it("formats attachment labels and file sizes", () => {
    expect(isMediaAttachment(createAttachment())).toBe(true);
    expect(isMediaAttachment(createAttachment({ kind: "FILE", contentType: "application/pdf" }))).toBe(false);
    expect(isAudioAttachment(createAttachment({ kind: "VOICE", contentType: "audio/ogg" }))).toBe(true);
    expect(attachmentLabel(createAttachment({ kind: "GIF" }))).toBe("GIF");
    expect(formatFileSize(512)).toBe("512 B");
    expect(formatFileSize(2048)).toBe("2.0 KB");
    expect(formatFileSize(3 * 1024 * 1024)).toBe("3.0 MB");
    expect(normalizeSharedMediaLinkUrl("t.me/team")).toBe("https://t.me/team");
    expect(normalizeSharedMediaLinkUrl("tg://call/room-77")).toBe("tg://call/room-77");
  });
});
