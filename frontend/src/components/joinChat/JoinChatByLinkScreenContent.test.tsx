import React from "react";
import { fireEvent, render } from "@testing-library/react-native";
import { JoinChatByLinkScreenContent } from "./JoinChatByLinkScreenContent";
import type { JoinChatByLinkScreenController } from "./useJoinChatByLinkController";
import type {
  ChatSummary,
  PublicChatDiscovery
} from "../../types";

function createChat(overrides: Partial<ChatSummary> = {}): ChatSummary {
  return {
    about: null,
    archived: false,
    autoDeleteSeconds: null,
    chatId: "chat-local",
    chatType: "GROUP",
    commentsEnabled: true,
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

function createDiscovery(overrides: Partial<PublicChatDiscovery> = {}): PublicChatDiscovery {
  return {
    about: null,
    chatId: "chat-1",
    chatType: "GROUP",
    forumEnabled: false,
    joinRequiresApproval: true,
    joined: false,
    memberCount: 12,
    photoAccessExpiresAt: null,
    photoUrl: null,
    publicUsername: "team",
    title: "Team",
    ...overrides
  };
}

function createController(
  overrides: Partial<JoinChatByLinkScreenController> = {}
): JoinChatByLinkScreenController {
  return {
    canJoin: true,
    discoveries: [],
    discovering: false,
    error: null,
    exactPublicChatMatch: null,
    handleInviteTokenChange: jest.fn(),
    handleJoin: jest.fn(async () => undefined),
    handleJoinDiscoveredChat: jest.fn(async () => undefined),
    handleOpenExactPublicChat: jest.fn(),
    inviteToken: "",
    joining: false,
    normalizedInviteToken: "",
    parsedLink: null,
    statusMessage: null,
    ...overrides
  };
}

describe("JoinChatByLinkScreenContent", () => {
  it("uses request-access as the primary action for exact approval-only discoveries", () => {
    const discovery = createDiscovery();
    const controller = createController({
      discoveries: [discovery],
      inviteToken: "@team",
      normalizedInviteToken: "@team"
    });

    const screen = render(
      <JoinChatByLinkScreenContent
        controller={controller}
        onClose={jest.fn()}
        onOpenParsedLink={jest.fn()}
      />
    );

    fireEvent.press(screen.getAllByText("Request access")[0]);

    expect(controller.handleJoinDiscoveredChat).toHaveBeenCalledWith(discovery);
    expect(controller.handleJoin).not.toHaveBeenCalled();
  });

  it("uses open-chat as the primary action for exact joined discoveries", () => {
    const discovery = createDiscovery({
      joined: true
    });
    const controller = createController({
      discoveries: [discovery],
      inviteToken: "@team",
      normalizedInviteToken: "@team"
    });

    const screen = render(
      <JoinChatByLinkScreenContent
        controller={controller}
        onClose={jest.fn()}
        onOpenParsedLink={jest.fn()}
      />
    );

    fireEvent.press(screen.getAllByText("Open chat")[0]);

    expect(controller.handleJoinDiscoveredChat).toHaveBeenCalledWith(discovery);
    expect(controller.handleJoin).not.toHaveBeenCalled();
  });

  it("uses open-chat as the primary action for exact local public-chat matches", () => {
    const controller = createController({
      exactPublicChatMatch: createChat(),
      inviteToken: "@team",
      normalizedInviteToken: "@team"
    });

    const screen = render(
      <JoinChatByLinkScreenContent
        controller={controller}
        onClose={jest.fn()}
        onOpenParsedLink={jest.fn()}
      />
    );

    fireEvent.press(screen.getAllByText("Open chat")[0]);

    expect(controller.handleOpenExactPublicChat).toHaveBeenCalled();
    expect(controller.handleJoinDiscoveredChat).not.toHaveBeenCalled();
    expect(controller.handleJoin).not.toHaveBeenCalled();
  });
});
