jest.mock("../screens/AuthScreen", () => ({
  AuthScreen: jest.fn(() => null)
}));
jest.mock("./RootMainTabsScreen", () => ({
  RootMainTabsScreen: jest.fn(() => null)
}));
jest.mock("./RootSurfaceScreens", () => ({
  RootCallScreenSurface: jest.fn(() => null),
  RootChatScreenSurface: jest.fn(() => null),
  RootForumTopicsScreenSurface: jest.fn(() => null),
  RootMembersScreenSurface: jest.fn(() => null)
}));

import React from "react";
import { act, render } from "@testing-library/react-native";
import { createRootPrimaryScreenRenderers } from "./createRootPrimaryScreenRenderers";
import { RootMainTabsScreen } from "./RootMainTabsScreen";
import { RootMembersScreenSurface } from "./RootSurfaceScreens";
import type { RootScreenRenderersInput } from "./rootScreenRendererTypes";
import type { AuthSession, ChatSummary } from "../types";

function createSession(): AuthSession {
  return {
    token: "token-1",
    refreshToken: "refresh-1",
    sessionId: "session-1",
    userId: "user-1",
    phoneNumber: "+375291234567",
    displayName: "Alex",
    username: "alex",
    accessTokenExpiresAt: null,
    refreshTokenExpiresAt: null,
    authMethod: "OTP",
    trustedSession: true
  };
}

function createChat(overrides: Partial<ChatSummary> = {}): ChatSummary {
  return {
    chatId: "chat-1",
    chatType: "DIRECT",
    title: "Chat 1",
    photoUrl: null,
    photoAccessExpiresAt: null,
    peerUserId: "peer-1",
    peerPhoneNumber: null,
    peerDisplayName: "Peer 1",
    peerOnline: false,
    peerLastSeenAt: null,
    peerIsBot: false,
    peerBotSupportsInline: false,
    peerBotWebAppUrl: null,
    publicUsername: null,
    about: null,
    autoDeleteSeconds: null,
    slowModeSeconds: null,
    forumEnabled: false,
    topicCount: 0,
    linkedDiscussionChatId: null,
    linkedDiscussionChatTitle: null,
    lastMessageAt: "2026-03-27T10:00:00.000Z",
    memberCount: 2,
    lastReadAt: null,
    unreadCount: 0,
    mentionCount: 0,
    replyCount: 0,
    archived: false,
    draftText: null,
    draftUpdatedAt: null,
    mutedUntil: null,
    pinned: false,
    pinOrder: null,
    pinnedMessageId: null,
    joinRequiresApproval: false,
    commentsEnabled: true,
    reactionsEnabled: true,
    crossPostingEnabled: false,
    markedUnread: false,
    ...overrides
  };
}

function createInput(overrides: Partial<RootScreenRenderersInput> = {}): RootScreenRenderersInput {
  return {
    acceptCurrentCall: jest.fn(),
    activeRootTab: "CHATS",
    botsEnabled: false,
    broadcastCallSignal: jest.fn(),
    callMediaState: {
      adaptationProfile: "BALANCED",
      callId: null,
      error: null,
      estimatedVideoSendBitrateKbps: null,
      localAudioEnabled: true,
      localScreenSharing: false,
      localStreamReady: false,
      localStreamUrl: null,
      localVideoEnabled: false,
      networkQuality: "UNKNOWN",
      peers: [],
      phase: "IDLE",
      requiresNativeBuild: false,
      screenShareSupported: false,
      speakerOn: false,
      targetVideoBitrateKbps: null
    },
    callsEnabled: true,
    callJoinLinksEnabled: false,
    callModerationEnabled: false,
    callScreenSharingEnabled: false,
    chatInfoRoute: null,
    chats: [],
    composeMode: null,
    createCurrentCallLink: jest.fn(),
    currentCall: null,
    currentCallLinks: [],
    declineCurrentCall: jest.fn(),
    directCallsEnabled: true,
    groupCallsEnabled: true,
    joinByLinkSeedToken: null,
    joinCallByLink: jest.fn(),
    leaveCurrentCall: jest.fn(),
    mediaViewer: null,
    membersChat: null,
    moderateCurrentCallParticipant: jest.fn(),
    openChat: jest.fn(),
    openChatFromNotification: jest.fn(),
    openDiscussionThread: jest.fn(),
    pendingChatFocus: null,
    pendingCreatedStoryFocus: null,
    recentCallSignals: [],
    refreshChats: jest.fn(),
    selectedBotMiniApp: null,
    selectedChat: null,
    selectedDiscussionThread: null,
    selectedForumTopic: null,
    session: createSession(),
    setActiveRootTab: jest.fn(),
    setChatMessages: jest.fn(),
    setMembersChat: jest.fn(),
    setModalRoute: jest.fn(),
    setPendingChatFocus: jest.fn(),
    setPendingCreatedStoryFocus: jest.fn(),
    setSelectedChat: jest.fn(),
    setSelectedDiscussionThread: jest.fn(),
    setSelectedForumTopic: jest.fn(),
    sharedMediaChat: null,
    startChatCall: jest.fn(),
    storiesEnabled: false,
    toggleCurrentScreenShare: jest.fn(),
    upsertChat: jest.fn(),
    ...overrides
  };
}

describe("createRootPrimaryScreenRenderers", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("opens a linked discussion chat from members using the cached chat when available", () => {
    const channelChat = createChat({
      chatId: "chat-channel",
      chatType: "CHANNEL",
      title: "Announcements",
      linkedDiscussionChatId: "chat-discussion",
      linkedDiscussionChatTitle: "Discussion"
    });
    const discussionChat = createChat({
      chatId: "chat-discussion",
      chatType: "GROUP",
      title: "Discussion"
    });
    const openChat = jest.fn();
    const setMembersChat = jest.fn();
    const renderers = createRootPrimaryScreenRenderers(
      createInput({
        chats: [channelChat, discussionChat],
        membersChat: channelChat,
        openChat,
        setMembersChat
      })
    );

    const screen = renderers.renderMembersScreen();
    expect(screen).not.toBeNull();
    render(screen!);

    const props = (RootMembersScreenSurface as jest.Mock).mock.calls[0][0];

    act(() => {
      props.onOpenDiscussionChat("chat-discussion");
    });

    expect(setMembersChat).toHaveBeenCalledWith(null);
    expect(openChat).toHaveBeenCalledWith(discussionChat);
  });

  it("falls back to notification-style open when members opens an uncached discussion chat", () => {
    const channelChat = createChat({
      chatId: "chat-channel",
      chatType: "CHANNEL",
      title: "Announcements",
      linkedDiscussionChatId: "chat-discussion",
      linkedDiscussionChatTitle: "Discussion"
    });
    const openChatFromNotification = jest.fn().mockResolvedValue(undefined);
    const setMembersChat = jest.fn();
    const renderers = createRootPrimaryScreenRenderers(
      createInput({
        chats: [channelChat],
        membersChat: channelChat,
        openChatFromNotification,
        setMembersChat
      })
    );

    const screen = renderers.renderMembersScreen();
    expect(screen).not.toBeNull();
    render(screen!);

    const props = (RootMembersScreenSurface as jest.Mock).mock.calls[0][0];

    act(() => {
      props.onOpenDiscussionChat("chat-discussion");
    });

    expect(setMembersChat).toHaveBeenCalledWith(null);
    expect(openChatFromNotification).toHaveBeenCalledWith(
      "token-1",
      "chat-discussion",
      "user-1"
    );
  });

  it("routes parsed join and chat links from the calls tab into the shared internal flow", () => {
    const openChatFromNotification = jest.fn().mockResolvedValue(undefined);
    const openChat = jest.fn();
    const setActiveRootTab = jest.fn();
    const setModalRoute = jest.fn();
    const localChat = createChat({
      chatId: "chat-local",
      chatType: "GROUP",
      publicUsername: "team",
      title: "Team"
    });
    const renderers = createRootPrimaryScreenRenderers(
      createInput({
        chats: [localChat],
        openChat,
        openChatFromNotification,
        setActiveRootTab,
        setModalRoute
      })
    );

    const screen = renderers.renderMainTabsScreen();
    expect(screen).not.toBeNull();
    render(screen!);

    const props = (RootMainTabsScreen as jest.Mock).mock.calls[0][0];

    expect(props.availableChats).toEqual([localChat]);

    act(() => {
      props.onOpenCallParsedLink({
        type: "JOIN",
        token: "@team"
      });
    });

    expect(setActiveRootTab).toHaveBeenCalledWith("CHATS");
    expect(setModalRoute).toHaveBeenCalledWith(null);
    expect(openChat).toHaveBeenCalledWith(localChat);

    act(() => {
      props.onOpenCallParsedLink({
        type: "CHAT",
        chatId: "chat-77",
        topicId: "topic-3"
      });
    });

    expect(openChatFromNotification).toHaveBeenCalledWith(
      "token-1",
      "chat-77",
      "user-1",
      "topic-3"
    );
  });
});
