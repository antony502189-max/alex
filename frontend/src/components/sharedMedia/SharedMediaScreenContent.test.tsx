import React from "react";
import { fireEvent, render } from "@testing-library/react-native";
import { SharedMediaScreenContent } from "./SharedMediaScreenContent";
import type {
  ChatSummary,
  MessageAttachment,
  SharedMediaBuckets
} from "../../types";

function createChat(overrides: Partial<ChatSummary> = {}): ChatSummary {
  return {
    about: null,
    archived: false,
    autoDeleteSeconds: null,
    chatId: "chat-1",
    chatType: "GROUP",
    commentsEnabled: true,
    crossPostingEnabled: false,
    draftText: null,
    draftUpdatedAt: null,
    forumEnabled: false,
    joinRequiresApproval: false,
    lastMessageAt: "2026-03-27T10:00:00.000Z",
    lastReadAt: null,
    linkedDiscussionChatId: null,
    linkedDiscussionChatTitle: null,
    markedUnread: false,
    memberCount: 3,
    mentionCount: 0,
    mutedUntil: null,
    peerBotSupportsInline: false,
    peerBotWebAppUrl: null,
    peerDisplayName: null,
    peerIsBot: false,
    peerLastSeenAt: null,
    peerOnline: false,
    peerPhoneNumber: null,
    peerUserId: null,
    photoAccessExpiresAt: null,
    photoUrl: null,
    pinOrder: null,
    pinned: false,
    pinnedMessageId: null,
    publicUsername: null,
    reactionsEnabled: true,
    replyCount: 0,
    slowModeSeconds: null,
    title: "Team chat",
    topicCount: 0,
    unreadCount: 0,
    ...overrides
  };
}

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

describe("SharedMediaScreenContent", () => {
  it("opens the source chat message from media, file, and link entries", () => {
    const mediaAttachment = createAttachment();
    const fileAttachment = createAttachment({
      attachmentId: "attachment-2",
      originalFileName: "doc.pdf",
      kind: "FILE",
      contentType: "application/pdf",
      previewUrl: null,
      thumbnailUrl: null
    });
    const buckets: SharedMediaBuckets = {
      chatId: "chat-1",
      loadedAt: "2026-03-27T12:00:00.000Z",
      media: [
        {
          chatId: "chat-1",
          messageId: "message-media",
          createdAt: "2026-03-27T11:00:00.000Z",
          senderDisplayName: "Alex",
          caption: null,
          attachment: mediaAttachment
        }
      ],
      files: [
        {
          chatId: "chat-1",
          messageId: "message-file",
          createdAt: "2026-03-27T10:30:00.000Z",
          senderDisplayName: "Alex",
          caption: null,
          attachment: fileAttachment
        }
      ],
      links: [
        {
          linkId: "link-1",
          chatId: "chat-1",
          messageId: "message-link",
          createdAt: "2026-03-27T10:15:00.000Z",
          url: "https://example.com",
          label: "Example"
        }
      ]
    };
    const onOpenMessage = jest.fn();

    const screen = render(
      <SharedMediaScreenContent
        chat={createChat()}
        controller={{
          buckets,
          error: null,
          handleOpenFileAttachment: jest.fn(),
          handleOpenLink: jest.fn(),
          handleRefresh: jest.fn(),
          handleToggleAudioAttachment: jest.fn(),
          loading: false,
          loadingAudioAttachmentId: null,
          mediaAttachments: [mediaAttachment],
          openingAttachmentId: null,
          openingLinkId: null,
          playingAudioAttachmentId: null,
          refreshing: false
        }}
        onClose={jest.fn()}
        onOpenMediaViewer={jest.fn()}
        onOpenMessage={onOpenMessage}
      />
    );

    const buttons = screen.getAllByText("View in chat");
    fireEvent.press(buttons[0]);
    fireEvent.press(buttons[1]);
    fireEvent.press(buttons[2]);

    expect(onOpenMessage).toHaveBeenNthCalledWith(1, "message-media", "2026-03-27T11:00:00.000Z");
    expect(onOpenMessage).toHaveBeenNthCalledWith(2, "message-file", "2026-03-27T10:30:00.000Z");
    expect(onOpenMessage).toHaveBeenNthCalledWith(3, "message-link", "2026-03-27T10:15:00.000Z");
  });
});
