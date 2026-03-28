import React from "react";
import { fireEvent, render } from "@testing-library/react-native";
import { CallsScreenContent } from "./CallsScreenContent";
import type { CallsScreenController } from "./useCallsScreenController";
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
  overrides: Partial<CallsScreenController> = {}
): CallsScreenController {
  return {
    callLinkToken: "",
    canJoinCallLink: false,
    error: null,
    handleCallLinkTokenChange: jest.fn(),
    loadRecentCalls: jest.fn(async () => undefined),
    missedCallsCount: 0,
    parsedLink: null,
    recentCalls: [],
    refreshing: false,
    ...overrides
  };
}

describe("CallsScreenContent", () => {
  it("shows a parsed-link quick action for non-call links in the calls tab", () => {
    const onOpenParsedLink = jest.fn();
    const controller = createController({
      callLinkToken: "t.me/team",
      parsedLink: {
        type: "JOIN",
        token: "@team"
      }
    });

    const screen = render(
      <CallsScreenContent
        availableChats={[]}
        callJoinLinksEnabled={true}
        controller={controller}
        onCallBack={jest.fn()}
        onClose={jest.fn()}
        onJoinCallLink={jest.fn()}
        onOpenChat={jest.fn()}
        onOpenParsedLink={onOpenParsedLink}
      />
    );

    fireEvent.press(screen.getByText("Open join flow"));

    expect(onOpenParsedLink).toHaveBeenCalledWith({
      type: "JOIN",
      token: "@team"
    });
  });

  it("opens a local public chat directly from the parsed-link quick action", () => {
    const onOpenChat = jest.fn();
    const controller = createController({
      callLinkToken: "t.me/team",
      parsedLink: {
        type: "JOIN",
        token: "@team"
      }
    });

    const screen = render(
      <CallsScreenContent
        availableChats={[createChat({ chatId: "chat-team", publicUsername: "team" })]}
        callJoinLinksEnabled={true}
        controller={controller}
        onCallBack={jest.fn()}
        onClose={jest.fn()}
        onJoinCallLink={jest.fn()}
        onOpenChat={onOpenChat}
        onOpenParsedLink={jest.fn()}
      />
    );

    fireEvent.press(screen.getByText("Open chat"));

    expect(onOpenChat).toHaveBeenCalledWith("chat-team");
  });

  it("shows an offline-aware empty state when call history failed to load", () => {
    const controller = createController({
      error: "Offline mode. Showing cached calls.",
      recentCalls: []
    });

    const screen = render(
      <CallsScreenContent
        availableChats={[]}
        callJoinLinksEnabled={true}
        controller={controller}
        onCallBack={jest.fn()}
        onClose={jest.fn()}
        onJoinCallLink={jest.fn()}
        onOpenChat={jest.fn()}
        onOpenParsedLink={jest.fn()}
      />
    );

    expect(screen.getByText("Call history unavailable")).toBeTruthy();
    expect(screen.getByText("No missed calls")).toBeTruthy();
  });
});
