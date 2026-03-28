jest.mock("../screens/AuthScreen", () => ({
  AuthScreen: jest.fn(() => null)
}));
jest.mock("../screens/ArchivedChatsScreen", () => ({
  ArchivedChatsScreen: jest.fn(() => null)
}));
jest.mock("../screens/BotDeveloperScreen", () => ({
  BotDeveloperScreen: jest.fn(() => null)
}));
jest.mock("../screens/BotMiniAppScreen", () => ({
  BotMiniAppScreen: jest.fn(() => null)
}));
jest.mock("../screens/CallScreen", () => ({
  CallScreen: jest.fn(() => null)
}));
jest.mock("../screens/ChatInfoScreen", () => ({
  ChatInfoScreen: jest.fn(() => null)
}));
jest.mock("../screens/ChatScreen", () => ({
  ChatScreen: jest.fn(() => null)
}));
jest.mock("../screens/CreateChatScreen", () => ({
  CreateChatScreen: jest.fn(() => null)
}));
jest.mock("../screens/CreateStoryScreen", () => ({
  CreateStoryScreen: jest.fn(() => null)
}));
jest.mock("../screens/FoldersScreen", () => ({
  FoldersScreen: jest.fn(() => null)
}));
jest.mock("../screens/ForumTopicsScreen", () => ({
  ForumTopicsScreen: jest.fn(() => null)
}));
jest.mock("../screens/GlobalSearchScreen", () => ({
  GlobalSearchScreen: jest.fn(() => null)
}));
jest.mock("../screens/JoinChatByLinkScreen", () => ({
  JoinChatByLinkScreen: jest.fn(() => null)
}));
jest.mock("../screens/MediaViewerScreen", () => ({
  MediaViewerScreen: jest.fn(() => null)
}));
jest.mock("../screens/MembersScreen", () => ({
  MembersScreen: jest.fn(() => null)
}));
jest.mock("../screens/SessionsScreen", () => ({
  SessionsScreen: jest.fn(() => null)
}));
jest.mock("../screens/SharedMediaScreen", () => ({
  SharedMediaScreen: jest.fn(() => null)
}));

import React from "react";
import { render } from "@testing-library/react-native";
import {
  RootChatInfoScreenSurface,
  RootChatScreenSurface,
  RootMembersScreenSurface
} from "./RootSurfaceScreens";
import { ChatInfoScreen } from "../screens/ChatInfoScreen";
import { ChatScreen } from "../screens/ChatScreen";
import { MembersScreen } from "../screens/MembersScreen";
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
    memberCount: 20,
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

describe("RootChatScreenSurface", () => {
  const mockedChatScreen = ChatScreen as jest.MockedFunction<typeof ChatScreen>;
  const mockedChatInfoScreen = ChatInfoScreen as jest.MockedFunction<typeof ChatInfoScreen>;
  const mockedMembersScreen = MembersScreen as jest.MockedFunction<typeof MembersScreen>;

  beforeEach(() => {
    mockedChatScreen.mockClear();
    mockedChatInfoScreen.mockClear();
    mockedMembersScreen.mockClear();
  });

  it("passes Chat Info entry point through for a group chat", () => {
    render(
      <RootChatScreenSurface
        botsEnabled={false}
        chats={[]}
        directCallsEnabled={true}
        groupCallsEnabled={true}
        onBack={jest.fn()}
        onConsumeInitialFocus={jest.fn()}
        onOpenBotMiniApp={jest.fn()}
        onOpenChatInfo={jest.fn()}
        onOpenDiscussionThread={jest.fn()}
        onOpenMediaViewer={jest.fn()}
        onOpenMembers={jest.fn()}
        onOpenParsedLink={jest.fn()}
        onRefreshChats={jest.fn()}
        onStartCall={jest.fn()}
        pendingChatFocus={null}
        selectedChat={createChat({ chatType: "GROUP" })}
        selectedDiscussionThread={null}
        selectedForumTopic={null}
        session={createSession()}
      />
    );

    expect(mockedChatScreen).toHaveBeenCalledWith(
      expect.objectContaining({
        onOpenChatInfo: expect.any(Function)
      }),
      undefined
    );
  });

  it("keeps Chat Info hidden for Saved Messages", () => {
    render(
      <RootChatScreenSurface
        botsEnabled={false}
        chats={[]}
        directCallsEnabled={true}
        groupCallsEnabled={true}
        onBack={jest.fn()}
        onConsumeInitialFocus={jest.fn()}
        onOpenBotMiniApp={jest.fn()}
        onOpenChatInfo={jest.fn()}
        onOpenDiscussionThread={jest.fn()}
        onOpenMediaViewer={jest.fn()}
        onOpenMembers={jest.fn()}
        onOpenParsedLink={jest.fn()}
        onRefreshChats={jest.fn()}
        onStartCall={jest.fn()}
        pendingChatFocus={null}
        selectedChat={createChat({ chatId: "saved", chatType: "SAVED", title: "Saved Messages" })}
        selectedDiscussionThread={null}
        selectedForumTopic={null}
        session={createSession()}
      />
    );

    expect(mockedChatScreen).toHaveBeenCalledWith(
      expect.objectContaining({
        onOpenChatInfo: undefined
      }),
      undefined
    );
  });

  it("passes parsed-link handler through to the chat screen", () => {
    const onOpenParsedLink = jest.fn();

    render(
      <RootChatScreenSurface
        botsEnabled={false}
        chats={[]}
        directCallsEnabled={true}
        groupCallsEnabled={true}
        onBack={jest.fn()}
        onConsumeInitialFocus={jest.fn()}
        onOpenBotMiniApp={jest.fn()}
        onOpenChatInfo={jest.fn()}
        onOpenDiscussionThread={jest.fn()}
        onOpenMediaViewer={jest.fn()}
        onOpenMembers={jest.fn()}
        onOpenParsedLink={onOpenParsedLink}
        onRefreshChats={jest.fn()}
        onStartCall={jest.fn()}
        pendingChatFocus={null}
        selectedChat={createChat({ chatType: "DIRECT" })}
        selectedDiscussionThread={null}
        selectedForumTopic={null}
        session={createSession()}
      />
    );

    expect(mockedChatScreen).toHaveBeenCalledWith(
      expect.objectContaining({
        onOpenParsedLink
      }),
      undefined
    );
  });

  it("gates chat info call actions by direct and group call capabilities", () => {
    render(
      <RootChatInfoScreenSurface
        chatInfoChat={createChat({ chatType: "DIRECT", peerUserId: "user-2" })}
        directCallsEnabled={false}
        groupCallsEnabled={true}
        onChatLeft={jest.fn()}
        onChatUpdated={jest.fn()}
        onClose={jest.fn()}
        onHistoryCleared={jest.fn()}
        onOpenDiscussionChat={jest.fn()}
        onOpenBotMiniApp={jest.fn()}
        onOpenMembers={jest.fn()}
        onOpenSharedMedia={jest.fn()}
        onStartCall={jest.fn()}
        session={createSession()}
      />
    );

    expect(mockedChatInfoScreen).toHaveBeenCalledWith(
      expect.objectContaining({
        onStartCall: undefined
      }),
      undefined
    );

    mockedChatInfoScreen.mockClear();

    render(
      <RootChatInfoScreenSurface
        chatInfoChat={createChat({ chatType: "GROUP" })}
        directCallsEnabled={false}
        groupCallsEnabled={true}
        onChatLeft={jest.fn()}
        onChatUpdated={jest.fn()}
        onClose={jest.fn()}
        onHistoryCleared={jest.fn()}
        onOpenDiscussionChat={jest.fn()}
        onOpenBotMiniApp={jest.fn()}
        onOpenMembers={jest.fn()}
        onOpenSharedMedia={jest.fn()}
        onStartCall={jest.fn()}
        session={createSession()}
      />
    );

    expect(mockedChatInfoScreen).toHaveBeenCalledWith(
      expect.objectContaining({
        onStartCall: expect.any(Function)
      }),
      undefined
    );
  });

  it("passes linked discussion navigation through to the members screen", () => {
    const onOpenDiscussionChat = jest.fn();

    render(
      <RootMembersScreenSurface
        membersChat={createChat({
          chatType: "CHANNEL",
          linkedDiscussionChatId: "chat-discussion",
          linkedDiscussionChatTitle: "Discussion"
        })}
        onChatLeft={jest.fn()}
        onChatUpdated={jest.fn()}
        onClose={jest.fn()}
        onHistoryCleared={jest.fn()}
        onOpenDiscussionChat={onOpenDiscussionChat}
        onOpenSharedMedia={jest.fn()}
        session={createSession()}
      />
    );

    expect(mockedMembersScreen).toHaveBeenCalledWith(
      expect.objectContaining({
        onOpenDiscussionChat
      }),
      undefined
    );
  });
});
