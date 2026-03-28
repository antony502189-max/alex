import React from "react";
import { fireEvent, render } from "@testing-library/react-native";
import { GlobalSearchScreenContent } from "./GlobalSearchScreenContent";
import type { GlobalSearchScreenController } from "./useGlobalSearchController";
import type { ChatSummary } from "../../types";

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
    publicUsername: "team",
    reactionsEnabled: true,
    replyCount: 0,
    slowModeSeconds: null,
    title: "Team",
    topicCount: 0,
    unreadCount: 0,
    ...overrides
  };
}

function createController(
  overrides: Partial<GlobalSearchScreenController> = {}
): GlobalSearchScreenController {
  return {
    error: null,
    exactPublicChatMatch: null,
    handleOpenChat: jest.fn(),
    handleOpenMessageResult: jest.fn(),
    handleOpenUser: jest.fn(async () => undefined),
    hasResults: false,
    loading: false,
    normalizedQuery: "",
    openingUserId: null,
    parsedLink: null,
    query: "",
    resultSummary: null,
    results: null,
    setQuery: jest.fn(),
    ...overrides
  };
}

describe("GlobalSearchScreenContent", () => {
  it("opens a local public chat directly from the parsed-link quick action", () => {
    const matchedChat = createChat();
    const onOpenChat = jest.fn();
    const onOpenParsedLink = jest.fn();
    const controller = createController({
      exactPublicChatMatch: matchedChat,
      parsedLink: {
        type: "JOIN",
        token: "@team"
      },
      query: "@team",
      normalizedQuery: "@team"
    });

    const screen = render(
      <GlobalSearchScreenContent
        controller={controller}
        onClose={jest.fn()}
        onOpenChat={onOpenChat}
        onOpenMessageResult={jest.fn()}
        onOpenParsedLink={onOpenParsedLink}
      />
    );

    fireEvent.press(screen.getByText("Open chat"));

    expect(onOpenChat).toHaveBeenCalledWith(matchedChat);
    expect(onOpenParsedLink).not.toHaveBeenCalled();
  });
});
