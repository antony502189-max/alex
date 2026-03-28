import React from "react";
import { act, render } from "@testing-library/react-native";
import { createRootModalScreenRenderers } from "./createRootModalScreenRenderers";
import {
  RootCreateStoryScreenSurface,
  RootGlobalSearchScreenSurface,
  RootJoinByLinkScreenSurface,
  RootSharedMediaScreenSurface,
  RootMediaViewerScreenSurface
} from "./RootSurfaceScreens";
import type { RootScreenRenderersInput } from "./rootScreenRendererTypes";
import type { AuthSession, ChatSummary } from "../types";

jest.mock("./RootSurfaceScreens", () => ({
  RootAddAccountScreenSurface: jest.fn(() => null),
  RootArchivedScreenSurface: jest.fn(() => null),
  RootBotDeveloperScreenSurface: jest.fn(() => null),
  RootBotMiniAppScreenSurface: jest.fn(() => null),
  RootChatInfoScreenSurface: jest.fn(() => null),
  RootCreateChatScreenSurface: jest.fn(() => null),
  RootCreateStoryScreenSurface: jest.fn(() => null),
  RootFoldersScreenSurface: jest.fn(() => null),
  RootGlobalSearchScreenSurface: jest.fn(() => null),
  RootJoinByLinkScreenSurface: jest.fn(() => null),
  RootMediaViewerScreenSurface: jest.fn(() => null),
  RootSessionsScreenSurface: jest.fn(() => null),
  RootSharedMediaScreenSurface: jest.fn(() => null)
}));

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

describe("createRootModalScreenRenderers", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("returns to the current chat context when opening a source message from the media viewer", () => {
    const selectedChat = createChat({
      chatId: "chat-77",
      title: "Team chat"
    });
    const setModalRoute = jest.fn();
    const setActiveRootTab = jest.fn();
    const setPendingChatFocus = jest.fn();
    const openChat = jest.fn();

    const renderers = createRootModalScreenRenderers(
      createInput({
        chats: [selectedChat],
        mediaViewer: {
          type: "MEDIA_VIEWER",
          attachments: [],
          attachmentId: "attachment-1",
          chatId: "chat-77",
          chatTitle: "Team chat"
        },
        openChat,
        selectedChat,
        setActiveRootTab,
        setModalRoute,
        setPendingChatFocus
      })
    );

    const mediaViewerScreen = renderers.renderMediaViewerScreen();
    expect(mediaViewerScreen).not.toBeNull();
    if (!mediaViewerScreen) {
      throw new Error("Expected media viewer screen to render");
    }

    render(mediaViewerScreen);

    const mediaViewerProps = (RootMediaViewerScreenSurface as jest.Mock).mock.calls[0][0];

    act(() => {
      mediaViewerProps.onOpenMessage("message-1", "2026-03-27T12:00:00.000Z");
    });

    expect(setModalRoute).toHaveBeenCalledWith(null);
    expect(setActiveRootTab).toHaveBeenCalledWith("CHATS");
    expect(setPendingChatFocus).toHaveBeenCalledWith({
      chatId: "chat-77",
      messageId: "message-1",
      createdAt: "2026-03-27T12:00:00.000Z"
    });
    expect(openChat).not.toHaveBeenCalled();
  });

  it("focuses the newly created story and returns the user to the stories tab", () => {
    const setActiveRootTab = jest.fn();
    const setModalRoute = jest.fn();
    const setPendingCreatedStoryFocus = jest.fn();
    const renderers = createRootModalScreenRenderers(
      createInput({
        setActiveRootTab,
        setModalRoute,
        setPendingCreatedStoryFocus,
        storiesEnabled: true
      })
    );

    const screen = renderers.renderCreateStoryScreen();
    expect(screen).not.toBeNull();
    render(screen!);

    const props = (RootCreateStoryScreenSurface as jest.Mock).mock.calls[0][0];

    act(() => {
      props.onCreated({
        ownerUserId: "user-1",
        storyId: "story-77"
      });
    });

    expect(setModalRoute).toHaveBeenCalledWith(null);
    expect(setPendingCreatedStoryFocus).toHaveBeenCalledWith({
      ownerUserId: "user-1",
      storyId: "story-77"
    });
    expect(setActiveRootTab).toHaveBeenCalledWith("STORIES");
  });

  it("opens matching local public chats directly from parsed global-search username links", () => {
    const setActiveRootTab = jest.fn();
    const openChat = jest.fn();
    const setModalRoute = jest.fn();
    const localChat = createChat({
      chatId: "chat-local",
      chatType: "GROUP",
      publicUsername: "team",
      title: "Team"
    });
    const renderers = createRootModalScreenRenderers(
      createInput({
        chats: [localChat],
        openChat,
        setActiveRootTab,
        setModalRoute
      })
    );

    const screen = renderers.renderGlobalSearchScreen();
    expect(screen).not.toBeNull();
    render(screen!);

    const props = (RootGlobalSearchScreenSurface as jest.Mock).mock.calls[0][0];

    expect(props.availableChats).toEqual([localChat]);

    act(() => {
      props.onOpenParsedLink({
        type: "JOIN",
        token: "@team"
      });
    });

    expect(setActiveRootTab).toHaveBeenCalledWith("CHATS");
    expect(setModalRoute).toHaveBeenCalledWith(null);
    expect(openChat).toHaveBeenCalledWith(localChat);
  });

  it("routes parsed call and chat links from global search into their final destinations", () => {
    const setActiveRootTab = jest.fn();
    const setModalRoute = jest.fn();
    const joinCallByLink = jest.fn();
    const openChatFromNotification = jest.fn();
    const renderers = createRootModalScreenRenderers(
      createInput({
        joinCallByLink,
        openChatFromNotification,
        setActiveRootTab,
        setModalRoute
      })
    );

    const screen = renderers.renderGlobalSearchScreen();
    expect(screen).not.toBeNull();
    render(screen!);

    const props = (RootGlobalSearchScreenSurface as jest.Mock).mock.calls[0][0];

    act(() => {
      props.onOpenParsedLink({
        type: "CALL",
        token: "room-77"
      });
    });

    expect(setModalRoute).toHaveBeenCalledWith(null);
    expect(setActiveRootTab).toHaveBeenCalledWith("CALLS");
    expect(joinCallByLink).toHaveBeenCalledWith("room-77");

    act(() => {
      props.onOpenParsedLink({
        type: "CHAT",
        chatId: "chat-99",
        topicId: "topic-5"
      });
    });

    expect(openChatFromNotification).toHaveBeenCalledWith(
      "token-1",
      "chat-99",
      "user-1",
      "topic-5"
    );
  });

  it("routes parsed call and chat links from join by link into their final destinations", () => {
    const setActiveRootTab = jest.fn();
    const setModalRoute = jest.fn();
    const joinCallByLink = jest.fn();
    const openChatFromNotification = jest.fn();
    const renderers = createRootModalScreenRenderers(
      createInput({
        joinCallByLink,
        openChatFromNotification,
        setActiveRootTab,
        setModalRoute
      })
    );

    const screen = renderers.renderJoinByLinkScreen();
    expect(screen).not.toBeNull();
    render(screen!);

    const props = (RootJoinByLinkScreenSurface as jest.Mock).mock.calls[0][0];

    expect(props.availableChats).toEqual([]);

    act(() => {
      props.onOpenParsedLink({
        type: "CALL",
        token: "room-99"
      });
    });

    expect(setModalRoute).toHaveBeenCalledWith(null);
    expect(setActiveRootTab).toHaveBeenCalledWith("CALLS");
    expect(joinCallByLink).toHaveBeenCalledWith("room-99");

    act(() => {
      props.onOpenParsedLink({
        type: "CHAT",
        chatId: "chat-88",
        topicId: null
      });
    });

    expect(openChatFromNotification).toHaveBeenCalledWith(
      "token-1",
      "chat-88",
      "user-1",
      null
    );
  });

  it("passes available chats into join by link so local public usernames can open directly", () => {
    const localChat = createChat({
      chatId: "chat-local",
      chatType: "GROUP",
      publicUsername: "team",
      title: "Team"
    });
    const renderers = createRootModalScreenRenderers(
      createInput({
        chats: [localChat]
      })
    );

    const screen = renderers.renderJoinByLinkScreen();
    expect(screen).not.toBeNull();
    render(screen!);

    const props = (RootJoinByLinkScreenSurface as jest.Mock).mock.calls[0][0];

    expect(props.availableChats).toEqual([localChat]);
  });

  it("opens an already joined discovery chat from join by link and closes the modal first", () => {
    const discoveredChat = createChat({
      chatId: "chat-open",
      chatType: "GROUP",
      title: "Open me"
    });
    const openChat = jest.fn();
    const setModalRoute = jest.fn();
    const renderers = createRootModalScreenRenderers(
      createInput({
        chats: [discoveredChat],
        openChat,
        setModalRoute
      })
    );

    const screen = renderers.renderJoinByLinkScreen();
    expect(screen).not.toBeNull();
    render(screen!);

    const props = (RootJoinByLinkScreenSurface as jest.Mock).mock.calls[0][0];

    act(() => {
      props.onOpenDiscoveryChat("chat-open");
    });

    expect(setModalRoute).toHaveBeenCalledWith(null);
    expect(openChat).toHaveBeenCalledWith(discoveredChat);
  });

  it("falls back to notification-style open when a joined discovery chat is not cached locally", () => {
    const openChatFromNotification = jest.fn().mockResolvedValue(undefined);
    const setModalRoute = jest.fn();
    const renderers = createRootModalScreenRenderers(
      createInput({
        openChatFromNotification,
        setModalRoute
      })
    );

    const screen = renderers.renderJoinByLinkScreen();
    expect(screen).not.toBeNull();
    render(screen!);

    const props = (RootJoinByLinkScreenSurface as jest.Mock).mock.calls[0][0];

    act(() => {
      props.onOpenDiscoveryChat("chat-open");
    });

    expect(setModalRoute).toHaveBeenCalledWith(null);
    expect(openChatFromNotification).toHaveBeenCalledWith(
      "token-1",
      "chat-open",
      "user-1"
    );
  });

  it("routes parsed call and chat links from shared media links into their final destinations", () => {
    const sharedMediaChat = createChat({
      chatId: "chat-shared",
      title: "Shared team"
    });
    const setActiveRootTab = jest.fn();
    const setModalRoute = jest.fn();
    const joinCallByLink = jest.fn();
    const openChatFromNotification = jest.fn();
    const renderers = createRootModalScreenRenderers(
      createInput({
        joinCallByLink,
        openChatFromNotification,
        setActiveRootTab,
        setModalRoute,
        sharedMediaChat
      })
    );

    const screen = renderers.renderSharedMediaScreen();
    expect(screen).not.toBeNull();
    render(screen!);

    const props = (RootSharedMediaScreenSurface as jest.Mock).mock.calls[0][0];

    act(() => {
      props.onOpenParsedLink({
        type: "CALL",
        token: "room-12"
      });
    });

    expect(setModalRoute).toHaveBeenCalledWith(null);
    expect(setActiveRootTab).toHaveBeenCalledWith("CALLS");
    expect(joinCallByLink).toHaveBeenCalledWith("room-12");

    act(() => {
      props.onOpenParsedLink({
        type: "CHAT",
        chatId: "chat-44",
        topicId: "topic-9"
      });
    });

    expect(openChatFromNotification).toHaveBeenCalledWith(
      "token-1",
      "chat-44",
      "user-1",
      "topic-9"
    );
  });

  it("starts a capability-allowed call from chat info and closes the modal first", () => {
    const chatInfoChat = createChat({
      chatId: "chat-call",
      chatType: "DIRECT",
      title: "Call chat"
    });
    const setModalRoute = jest.fn();
    const startChatCall = jest.fn().mockResolvedValue(undefined);
    const renderers = createRootModalScreenRenderers(
      createInput({
        chatInfoRoute: {
          type: "CHAT_INFO",
          chatId: "chat-call"
        },
        chats: [chatInfoChat],
        setModalRoute,
        startChatCall
      })
    );

    const screen = renderers.renderChatInfoScreen();
    expect(screen).not.toBeNull();
    render(screen!);

    const props = (require("./RootSurfaceScreens").RootChatInfoScreenSurface as jest.Mock).mock.calls[0][0];

    act(() => {
      props.onStartCall("VOICE");
    });

    expect(setModalRoute).toHaveBeenCalledWith(null);
    expect(startChatCall).toHaveBeenCalledWith("chat-call", "VOICE");
  });

  it("opens a linked discussion chat from chat info and closes the modal first", () => {
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
    const setModalRoute = jest.fn();
    const renderers = createRootModalScreenRenderers(
      createInput({
        chatInfoRoute: {
          type: "CHAT_INFO",
          chatId: "chat-channel"
        },
        chats: [channelChat, discussionChat],
        openChat,
        setModalRoute
      })
    );

    const screen = renderers.renderChatInfoScreen();
    expect(screen).not.toBeNull();
    render(screen!);

    const props = (require("./RootSurfaceScreens").RootChatInfoScreenSurface as jest.Mock).mock.calls[0][0];

    act(() => {
      props.onOpenDiscussionChat("chat-discussion");
    });

    expect(setModalRoute).toHaveBeenCalledWith(null);
    expect(openChat).toHaveBeenCalledWith(discussionChat);
  });

  it("falls back to notification-style open when the linked discussion chat is not cached locally", () => {
    const channelChat = createChat({
      chatId: "chat-channel",
      chatType: "CHANNEL",
      title: "Announcements",
      linkedDiscussionChatId: "chat-discussion",
      linkedDiscussionChatTitle: "Discussion"
    });
    const openChatFromNotification = jest.fn().mockResolvedValue(undefined);
    const setModalRoute = jest.fn();
    const renderers = createRootModalScreenRenderers(
      createInput({
        chatInfoRoute: {
          type: "CHAT_INFO",
          chatId: "chat-channel"
        },
        chats: [channelChat],
        openChatFromNotification,
        setModalRoute
      })
    );

    const screen = renderers.renderChatInfoScreen();
    expect(screen).not.toBeNull();
    render(screen!);

    const props = (require("./RootSurfaceScreens").RootChatInfoScreenSurface as jest.Mock).mock.calls[0][0];

    act(() => {
      props.onOpenDiscussionChat("chat-discussion");
    });

    expect(setModalRoute).toHaveBeenCalledWith(null);
    expect(openChatFromNotification).toHaveBeenCalledWith(
      "token-1",
      "chat-discussion",
      "user-1"
    );
  });
});
