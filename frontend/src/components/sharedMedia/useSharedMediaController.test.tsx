jest.mock("expo-av", () => ({
  Audio: {
    Sound: {
      createAsync: jest.fn()
    }
  }
}));

jest.mock("expo-sharing", () => ({
  isAvailableAsync: jest.fn(),
  shareAsync: jest.fn()
}));

jest.mock("../../services/api", () => ({
  api: {
    getMessages: jest.fn()
  }
}));

jest.mock("../../services/attachmentTransfers", () => ({
  attachmentTransfers: {
    downloadAttachment: jest.fn()
  }
}));

import * as Sharing from "expo-sharing";
import { Audio } from "expo-av";
import { act, renderHook, waitFor } from "@testing-library/react-native";
import { Linking } from "react-native";
import { api } from "../../services/api";
import { attachmentTransfers } from "../../services/attachmentTransfers";
import { useMediaStore } from "../../store/useMediaStore";
import type {
  ChatMessage,
  ChatSummary,
  MessageAttachment
} from "../../types";
import { useSharedMediaController } from "./useSharedMediaController";

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

describe("useSharedMediaController", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    useMediaStore.setState({
      bucketsByChatId: {}
    });
  });

  it("loads buckets, derives media attachments, and caches them in the media store", async () => {
    const imageAttachment = createAttachment();
    const fileAttachment = createAttachment({
      attachmentId: "attachment-2",
      originalFileName: "notes.pdf",
      kind: "FILE",
      contentType: "application/pdf",
      previewUrl: null,
      thumbnailUrl: null
    });

    (api.getMessages as jest.Mock).mockResolvedValue([
      createMessage({
        messageId: "message-1",
        attachments: [imageAttachment]
      }),
      createMessage({
        messageId: "message-2",
        attachments: [fileAttachment],
        text: "Link alex://join/team"
      })
    ]);

    const { result } = renderHook(() =>
      useSharedMediaController({
        chat: createChat(),
        token: "token-1"
      })
    );

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
      expect(result.current.buckets?.media).toHaveLength(1);
      expect(result.current.buckets?.files).toHaveLength(1);
      expect(result.current.buckets?.links).toHaveLength(2);
    });

    expect(result.current.mediaAttachments).toEqual([imageAttachment]);
    expect(useMediaStore.getState().bucketsByChatId["chat-1"]?.media).toHaveLength(1);
    expect(api.getMessages).toHaveBeenCalledWith("token-1", "chat-1", 120);
  });

  it("opens files through sharing and opens links through Linking", async () => {
    const fileAttachment = createAttachment({
      attachmentId: "attachment-2",
      originalFileName: "notes.pdf",
      kind: "FILE",
      contentType: "application/pdf",
      previewUrl: null,
      thumbnailUrl: null
    });
    const openUrlSpy = jest.spyOn(Linking, "openURL").mockResolvedValue(undefined);

    (api.getMessages as jest.Mock).mockResolvedValue([]);
    (attachmentTransfers.downloadAttachment as jest.Mock).mockResolvedValue("file:///notes.pdf");
    (Sharing.isAvailableAsync as jest.Mock).mockResolvedValue(true);
    (Sharing.shareAsync as jest.Mock).mockResolvedValue(undefined);

    const { result } = renderHook(() =>
      useSharedMediaController({
        chat: createChat(),
        token: "token-1"
      })
    );

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
    });

    await act(async () => {
      await result.current.handleOpenFileAttachment(fileAttachment);
    });

    expect(attachmentTransfers.downloadAttachment).toHaveBeenCalledWith("token-1", fileAttachment);
    expect(Sharing.shareAsync).toHaveBeenCalledWith("file:///notes.pdf");

    await act(async () => {
      await result.current.handleOpenLink("link-1", "https://example.com");
    });

    expect(openUrlSpy).toHaveBeenCalledWith("https://example.com");
    openUrlSpy.mockRestore();
  });

  it("routes recognized app links internally and normalizes scheme-less telegram links", async () => {
    const openUrlSpy = jest.spyOn(Linking, "openURL").mockResolvedValue(undefined);
    const onOpenParsedLink = jest.fn();

    (api.getMessages as jest.Mock).mockResolvedValue([]);

    const { result } = renderHook(() =>
      useSharedMediaController({
        chat: createChat(),
        onOpenParsedLink,
        token: "token-1"
      })
    );

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
    });

    await act(async () => {
      await result.current.handleOpenLink("link-1", "tg://call/room-77");
    });

    expect(onOpenParsedLink).toHaveBeenCalledWith({
      type: "CALL",
      token: "room-77"
    });
    expect(openUrlSpy).not.toHaveBeenCalled();

    await act(async () => {
      await result.current.handleOpenLink("link-2", "example.com/docs");
    });

    expect(openUrlSpy).toHaveBeenCalledWith("example.com/docs");

    openUrlSpy.mockClear();

    await act(async () => {
      await result.current.handleOpenLink("link-3", "t.me/team");
    });

    expect(onOpenParsedLink).toHaveBeenCalledWith({
      type: "JOIN",
      token: "@team"
    });
    expect(openUrlSpy).not.toHaveBeenCalled();
    openUrlSpy.mockRestore();
  });

  it("plays and stops shared audio attachments inside the screen", async () => {
    const audioAttachment = createAttachment({
      attachmentId: "attachment-audio",
      originalFileName: "voice.ogg",
      kind: "VOICE",
      contentType: "audio/ogg",
      previewUrl: null,
      thumbnailUrl: null,
      durationMs: 4200
    });
    const unloadAsync = jest.fn().mockResolvedValue(undefined);
    const setOnPlaybackStatusUpdate = jest.fn();

    (api.getMessages as jest.Mock).mockResolvedValue([]);
    (attachmentTransfers.downloadAttachment as jest.Mock).mockResolvedValue("file:///voice.ogg");
    (Audio.Sound.createAsync as jest.Mock).mockResolvedValue({
      sound: {
        unloadAsync,
        setOnPlaybackStatusUpdate
      }
    });

    const { result } = renderHook(() =>
      useSharedMediaController({
        chat: createChat(),
        token: "token-1"
      })
    );

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
    });

    await act(async () => {
      await result.current.handleToggleAudioAttachment(audioAttachment);
    });

    expect(attachmentTransfers.downloadAttachment).toHaveBeenCalledWith("token-1", audioAttachment);
    expect(Audio.Sound.createAsync).toHaveBeenCalledWith(
      { uri: "file:///voice.ogg" },
      { shouldPlay: true }
    );
    expect(result.current.playingAudioAttachmentId).toBe("attachment-audio");
    expect(setOnPlaybackStatusUpdate).toHaveBeenCalled();

    await act(async () => {
      await result.current.handleToggleAudioAttachment(audioAttachment);
    });

    expect(unloadAsync).toHaveBeenCalled();
    expect(result.current.playingAudioAttachmentId).toBeNull();
  });
});
