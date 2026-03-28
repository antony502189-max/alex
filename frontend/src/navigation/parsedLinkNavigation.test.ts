import { handleParsedLinkIntent } from "./parsedLinkNavigation";
import type { AuthSession, ChatSummary } from "../types";

function createSession(overrides: Partial<AuthSession> = {}): AuthSession {
  return {
    token: "token-1",
    refreshToken: "refresh-1",
    sessionId: "session-1",
    userId: "user-1",
    phoneNumber: "+375291111111",
    displayName: "Alex",
    username: "alex",
    accessTokenExpiresAt: null,
    refreshTokenExpiresAt: null,
    authMethod: "OTP",
    trustedSession: true,
    ...overrides
  };
}

describe("handleParsedLinkIntent", () => {
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

  it("routes join, call, and chat intents to the shared root flows", () => {
    const setActiveRootTab = jest.fn();
    const setModalRoute = jest.fn();
    const joinCallByLink = jest.fn();
    const openChat = jest.fn();
    const openChatFromNotification = jest.fn();

    handleParsedLinkIntent({
      availableChats: [],
      joinCallByLink,
      openChat,
      openChatFromNotification,
      parsedLink: {
        type: "JOIN",
        token: "@team"
      },
      session: createSession(),
      setActiveRootTab,
      setModalRoute
    });

    expect(setActiveRootTab).toHaveBeenCalledWith("CHATS");
    expect(setModalRoute).toHaveBeenCalledWith({
      type: "JOIN_BY_LINK",
      seedToken: "@team"
    });

    handleParsedLinkIntent({
      availableChats: [],
      joinCallByLink,
      openChat,
      openChatFromNotification,
      parsedLink: {
        type: "CALL",
        token: "room-55"
      },
      session: createSession(),
      setActiveRootTab,
      setModalRoute
    });

    expect(joinCallByLink).toHaveBeenCalledWith("room-55");
    expect(setActiveRootTab).toHaveBeenCalledWith("CALLS");

    handleParsedLinkIntent({
      availableChats: [],
      joinCallByLink,
      openChat,
      openChatFromNotification,
      parsedLink: {
        type: "CHAT",
        chatId: "chat-9",
        topicId: "topic-3"
      },
      session: createSession(),
      setActiveRootTab,
      setModalRoute
    });

    expect(openChatFromNotification).toHaveBeenCalledWith(
      "token-1",
      "chat-9",
      "user-1",
      "topic-3"
    );
  });

  it("opens a matching local public chat directly for parsed username joins", () => {
    const setActiveRootTab = jest.fn();
    const setModalRoute = jest.fn();
    const joinCallByLink = jest.fn();
    const openChat = jest.fn();
    const openChatFromNotification = jest.fn();
    const localChat = createChat({
      chatId: "chat-team",
      publicUsername: "team",
      title: "Team"
    });

    handleParsedLinkIntent({
      availableChats: [localChat],
      joinCallByLink,
      openChat,
      openChatFromNotification,
      parsedLink: {
        type: "JOIN",
        token: "@team"
      },
      session: createSession(),
      setActiveRootTab,
      setModalRoute
    });

    expect(setModalRoute).toHaveBeenCalledWith(null);
    expect(setActiveRootTab).toHaveBeenCalledWith("CHATS");
    expect(openChat).toHaveBeenCalledWith(localChat);
  });
});
