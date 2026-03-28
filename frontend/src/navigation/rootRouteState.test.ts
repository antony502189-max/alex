import { buildDesiredRoutes, routeStacksEqual } from "./rootRouteState";
import type {
  AuthSession,
  CallSession,
  ChatSummary,
  ForumTopic
} from "../types";
import type { AppModalRoute } from "./types";

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

function createForumTopic(overrides: Partial<ForumTopic> = {}): ForumTopic {
  return {
    topicId: "topic-1",
    chatId: "chat-1",
    title: "General",
    iconEmoji: null,
    generalTopic: false,
    closed: false,
    hidden: false,
    createdBy: "user-1",
    createdAt: "2026-03-27T10:00:00.000Z",
    updatedAt: "2026-03-27T10:00:00.000Z",
    lastMessageAt: null,
    ...overrides
  };
}

function createCall(overrides: Partial<CallSession> = {}): CallSession {
  return {
    callId: "call-1",
    chatId: "chat-1",
    createdByUserId: "user-1",
    kind: "VOICE",
    mode: "DIRECT",
    status: "ACTIVE",
    startedAt: "2026-03-27T10:00:00.000Z",
    answeredAt: "2026-03-27T10:00:30.000Z",
    endedAt: null,
    viewerCanModerate: false,
    viewerCanManageLinks: false,
    participants: [
      {
        userId: "user-1",
        displayName: "Alex",
        phoneNumber: null,
        photoUrl: null,
        photoAccessExpiresAt: null,
        state: "JOINED",
        invitedAt: "2026-03-27T10:00:00.000Z",
        joinedAt: "2026-03-27T10:00:05.000Z",
        leftAt: null,
        audioPublishingAllowed: true,
        videoPublishingAllowed: true,
        screenShareAllowed: true,
        screenSharing: false,
        moderatedByUserId: null,
        moderatedAt: null
      }
    ],
    ...overrides
  };
}

function createModalRoute(route: AppModalRoute): AppModalRoute {
  return route;
}

describe("rootRouteState", () => {
  it("returns auth route when session is missing", () => {
    expect(
      buildDesiredRoutes({
        session: null,
        selectedChat: null,
        selectedForumTopic: null,
        selectedDiscussionThread: null,
        membersChat: null,
        composeMode: null,
        modalRoute: null,
        mediaViewer: null,
        sharedMediaChat: null,
        selectedBotMiniApp: null,
        currentCall: null
      })
    ).toEqual([{ name: "AUTH" }]);
  });

  it("builds forum, members, and call routes for an authenticated user", () => {
    const session = createSession();
    const forumChat = createChat({
      chatId: "forum-1",
      chatType: "GROUP",
      forumEnabled: true,
      memberCount: 12,
      title: "Forum chat"
    });
    const currentCall = createCall({
      callId: "call-77",
      chatId: forumChat.chatId,
      mode: "GROUP"
    });

    expect(
      buildDesiredRoutes({
        session,
        selectedChat: forumChat,
        selectedForumTopic: null,
        selectedDiscussionThread: null,
        membersChat: forumChat,
        composeMode: null,
        modalRoute: null,
        mediaViewer: null,
        sharedMediaChat: null,
        selectedBotMiniApp: null,
        currentCall
      })
    ).toEqual([
      { name: "MAIN_TABS" },
      {
        name: "FORUM_TOPICS",
        params: {
          chatId: "forum-1"
        }
      },
      {
        name: "MEMBERS",
        params: {
          chatId: "forum-1"
        }
      },
      {
        name: "CALL",
        params: {
          callId: "call-77"
        }
      }
    ]);
  });

  it("keeps create-chat flow above other modal routes", () => {
    const session = createSession();
    const selectedChat = createChat({
      chatId: "chat-22"
    });
    const joinByLinkModal = createModalRoute({
      type: "JOIN_BY_LINK",
      seedToken: "invite-token"
    });

    expect(
      buildDesiredRoutes({
        session,
        selectedChat,
        selectedForumTopic: createForumTopic({
          chatId: "chat-22"
        }),
        selectedDiscussionThread: null,
        membersChat: null,
        composeMode: "group",
        modalRoute: joinByLinkModal,
        mediaViewer: null,
        sharedMediaChat: null,
        selectedBotMiniApp: null,
        currentCall: null
      })
    ).toEqual([
      { name: "MAIN_TABS" },
      {
        name: "CHAT",
        params: {
          chatId: "chat-22",
          topicId: "topic-1",
          threadRootMessageId: null
        }
      },
      {
        name: "CREATE_CHAT",
        params: {
          mode: "group"
        }
      }
    ]);
  });

  it("builds chat stack and compares route stacks", () => {
    const routes = buildDesiredRoutes({
      session: createSession(),
      selectedChat: createChat({
        chatId: "chat-9"
      }),
      selectedForumTopic: null,
      selectedDiscussionThread: null,
      membersChat: null,
      composeMode: null,
      modalRoute: null,
      mediaViewer: null,
      sharedMediaChat: null,
      selectedBotMiniApp: null,
      currentCall: null
    });

    expect(routes).toEqual([
      { name: "MAIN_TABS" },
      {
        name: "CHAT",
        params: {
          chatId: "chat-9",
          topicId: null,
          threadRootMessageId: null
        }
      }
    ]);

    expect(routeStacksEqual(routes, routes)).toBe(true);
    expect(
      routeStacksEqual(routes, [
        { name: "MAIN_TABS" },
        {
          name: "CHAT",
          params: {
            chatId: "chat-10",
            topicId: null,
            threadRootMessageId: null
          }
        }
      ])
    ).toBe(false);
  });

  it("keeps shared media under the media viewer when opened from the media tab", () => {
    const routes = buildDesiredRoutes({
      session: createSession(),
      selectedChat: createChat({
        chatId: "chat-55"
      }),
      selectedForumTopic: null,
      selectedDiscussionThread: null,
      membersChat: null,
      composeMode: null,
      modalRoute: createModalRoute({
        type: "MEDIA_VIEWER",
        attachments: [],
        attachmentId: "attachment-1",
        chatId: "chat-55",
        chatTitle: "Chat 55",
        returnToSharedMediaChatId: "chat-55"
      }),
      mediaViewer: {
        attachmentId: "attachment-1",
        chatId: "chat-55",
        returnToSharedMediaChatId: "chat-55"
      },
      sharedMediaChat: createChat({
        chatId: "chat-55"
      }),
      selectedBotMiniApp: null,
      currentCall: null
    });

    expect(routes).toEqual([
      { name: "MAIN_TABS" },
      {
        name: "CHAT",
        params: {
          chatId: "chat-55",
          topicId: null,
          threadRootMessageId: null
        }
      },
      {
        name: "SHARED_MEDIA",
        params: {
          chatId: "chat-55"
        }
      },
      {
        name: "MEDIA_VIEWER",
        params: {
          chatId: "chat-55",
          attachmentId: "attachment-1"
        }
      }
    ]);
  });
});
