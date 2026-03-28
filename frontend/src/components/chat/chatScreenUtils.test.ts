import type { MessageAttachment, ScheduledMessage } from "../../types";
import {
  formatCooldown,
  formatDuration,
  getImagePreviewHeight,
  mergeScheduledMessages,
  parseInlineBotQuery
} from "./chatScreenUtils";

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

describe("chatScreenUtils", () => {
  it("merges scheduled messages by key and prefers synced over queued copies", () => {
    const merged = mergeScheduledMessages([
      {
        ...baseScheduledMessage,
        clientMessageId: "client-1",
        createdAt: "2026-03-27T10:00:00.000Z",
        scheduledAt: "2026-03-27T10:20:00.000Z",
        status: "QUEUED"
      },
      {
        ...baseScheduledMessage,
        clientMessageId: "client-1",
        createdAt: "2026-03-27T10:01:00.000Z",
        scheduledAt: "2026-03-27T10:20:00.000Z",
        status: "PENDING",
        text: "server copy"
      },
      {
        ...baseScheduledMessage,
        scheduledMessageId: "scheduled-2",
        scheduledAt: "2026-03-27T10:05:00.000Z",
        text: "earlier"
      }
    ]);

    expect(merged).toHaveLength(2);
    expect(merged[0].scheduledMessageId).toBe("scheduled-2");
    expect(merged[1].text).toBe("server copy");
    expect(merged[1].status).toBe("PENDING");
  });

  it("parses inline bot queries and normalizes usernames", () => {
    expect(parseInlineBotQuery("  @Helper_Bot weather Minsk")).toEqual({
      botUsername: "helper_bot",
      query: "weather Minsk"
    });
    expect(parseInlineBotQuery("hello world")).toBeNull();
  });

  it("formats durations, cooldowns, and image preview heights", () => {
    expect(formatDuration(90500)).toBe("1:31");
    expect(formatCooldown(4500)).toBe("5s");
    expect(formatCooldown(65000)).toBe("1m 5s");
    expect(getImagePreviewHeight(baseAttachment)).toBe(165);
    expect(getImagePreviewHeight({ ...baseAttachment, width: null })).toBe(220);
  });
});
