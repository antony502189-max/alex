import React from "react";
import { Share } from "react-native";
import { fireEvent, render, waitFor } from "@testing-library/react-native";
import { api } from "../services/api";
import { ChatInfoScreen } from "./ChatInfoScreen";
import type { ChatSummary } from "../types";

jest.mock("../services/api", () => ({
  api: {
    blockUser: jest.fn(),
    clearHistory: jest.fn(),
    getBotCommands: jest.fn(),
    getBlockedUsers: jest.fn(),
    getChats: jest.fn(),
    leaveChat: jest.fn(),
    markChatUnread: jest.fn(),
    muteChat: jest.fn(),
    pinChatToList: jest.fn(),
    reportChat: jest.fn(),
    reportUser: jest.fn(),
    setChatArchived: jest.fn(),
    unblockUser: jest.fn(),
    unpinChatFromList: jest.fn()
  }
}));

function createChat(overrides: Partial<ChatSummary> = {}): ChatSummary {
  return {
    about: null,
    archived: false,
    autoDeleteSeconds: null,
    chatId: "chat-1",
    chatType: "GROUP",
    commentsEnabled: false,
    crossPostingEnabled: false,
    draftText: null,
    draftUpdatedAt: null,
    forumEnabled: false,
    joinRequiresApproval: false,
    lastMessageAt: "2026-03-28T10:00:00.000Z",
    lastReadAt: null,
    linkedDiscussionChatId: null,
    linkedDiscussionChatTitle: null,
    markedUnread: false,
    memberCount: 12,
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
    title: "Team",
    topicCount: 0,
    unreadCount: 0,
    ...overrides
  };
}

describe("ChatInfoScreen", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (api.getBlockedUsers as jest.Mock).mockResolvedValue([]);
    (api.getBotCommands as jest.Mock).mockResolvedValue([]);
  });

  it("shows call quick actions when the surface provides start-call capability", () => {
    const onStartCall = jest.fn();
    const screen = render(
      <ChatInfoScreen
        chat={createChat()}
        currentUserId="user-1"
        onClose={jest.fn()}
        onStartCall={onStartCall}
        token="token-1"
      />
    );

    fireEvent.press(screen.getByText("Voice call"));
    fireEvent.press(screen.getByText("Video call"));

    expect(onStartCall).toHaveBeenNthCalledWith(1, "VOICE");
    expect(onStartCall).toHaveBeenNthCalledWith(2, "VIDEO");
  });

  it("treats direct bot chats as bot profiles, hides call actions, and shows bot commands", async () => {
    (api.getBotCommands as jest.Mock).mockResolvedValue([
      { command: "/start", description: "Start the bot" }
    ]);

    const screen = render(
      <ChatInfoScreen
        chat={createChat({
          chatType: "DIRECT",
          peerBotSupportsInline: true,
          peerBotWebAppUrl: "https://example.com/bot-app",
          peerDisplayName: "Gif Bot",
          peerIsBot: true,
          peerPhoneNumber: null,
          peerUserId: "bot-1",
          publicUsername: "gifbot",
          title: "Gif Bot"
        })}
        currentUserId="user-1"
        onClose={jest.fn()}
        onStartCall={jest.fn()}
        token="token-1"
      />
    );

    expect(screen.getByText("Bot profile")).toBeTruthy();
    expect(screen.getByText("bot account - inline enabled - mini app available")).toBeTruthy();
    expect(screen.queryByText("Voice call")).toBeNull();
    expect(screen.queryByText("Video call")).toBeNull();

    await waitFor(() => {
      expect(screen.getByText("/start")).toBeTruthy();
      expect(screen.getByText("Start the bot")).toBeTruthy();
    });

    expect(api.getBotCommands).toHaveBeenCalledWith("token-1", "bot-1");
  });

  it("shares the public chat link when a public username is available", () => {
    const shareSpy = jest.spyOn(Share, "share").mockResolvedValue({
      action: "sharedAction"
    });
    const screen = render(
      <ChatInfoScreen
        chat={createChat({
          chatType: "GROUP",
          publicUsername: "team"
        })}
        currentUserId="user-1"
        onClose={jest.fn()}
        token="token-1"
      />
    );

    fireEvent.press(screen.getByText("Share public link"));

    expect(shareSpy).toHaveBeenCalledWith({
      message: "https://alex.example/join/team",
      url: "https://alex.example/join/team"
    });

    shareSpy.mockRestore();
  });

  it("opens the linked discussion group when the channel has one", () => {
    const onOpenDiscussionChat = jest.fn();
    const screen = render(
      <ChatInfoScreen
        chat={createChat({
          chatType: "CHANNEL",
          linkedDiscussionChatId: "chat-discussion",
          linkedDiscussionChatTitle: "Team Discussion"
        })}
        currentUserId="user-1"
        onClose={jest.fn()}
        onOpenDiscussionChat={onOpenDiscussionChat}
        token="token-1"
      />
    );

    fireEvent.press(screen.getByText("Open discussion group"));

    expect(onOpenDiscussionChat).toHaveBeenCalledWith("chat-discussion");
  });
});
