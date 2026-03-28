jest.mock("../../services/api", () => ({
  api: {
    addChatMembers: jest.fn(),
    approveChatJoinRequest: jest.fn(),
    banChatMember: jest.fn(),
    clearHistory: jest.fn(),
    createChatInviteLink: jest.fn(),
    declineChatJoinRequest: jest.fn(),
    deleteChatPhoto: jest.fn(),
    getChatAnalytics: jest.fn(),
    getChatBans: jest.fn(),
    getChatInviteLinks: jest.fn(),
    getChatJoinRequests: jest.fn(),
    getChatMembers: jest.fn(),
    leaveChat: jest.fn(),
    markChatUnread: jest.fn(),
    muteChat: jest.fn(),
    pinChatToList: jest.fn(),
    removeChatMember: jest.fn(),
    reportChat: jest.fn(),
    revokeChatInviteLink: jest.fn(),
    searchUsers: jest.fn(),
    setChatArchived: jest.fn(),
    unbanChatMember: jest.fn(),
    unpinChatFromList: jest.fn(),
    updateChatMemberPermissions: jest.fn(),
    updateChatMemberRestriction: jest.fn(),
    updateChatProfile: jest.fn(),
    updateChatPublicUsername: jest.fn(),
    updateMemberRole: jest.fn(),
    uploadChatPhoto: jest.fn()
  }
}));

jest.mock("../../services/imagePicker", () => ({
  pickSingleImage: jest.fn()
}));

jest.mock("@react-navigation/native", () => {
  const React = require("react");
  return {
    useFocusEffect: (callback: () => void | (() => void)) => {
      React.useEffect(() => callback(), [callback]);
    }
  };
});

import { act, renderHook, waitFor } from "@testing-library/react-native";
import { api } from "../../services/api";
import { useAppStore } from "../../store/useAppStore";
import type {
  AccountState,
  AuthSession,
  ChatAnalytics,
  ChatBan,
  ChatInviteLink,
  ChatJoinRequest,
  ChatMember,
  ChatSummary,
  UserSearchResult
} from "../../types";
import { useMembersScreenController } from "./useMembersScreenController";

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
    about: "Team chat",
    archived: false,
    autoDeleteSeconds: null,
    chatId: "chat-1",
    chatType: "GROUP",
    commentsEnabled: true,
    crossPostingEnabled: false,
    draftText: null,
    draftUpdatedAt: null,
    forumEnabled: false,
    joinRequiresApproval: true,
    lastMessageAt: "2026-03-27T10:00:00.000Z",
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

function createMember(overrides: Partial<ChatMember> = {}): ChatMember {
  return {
    userId: "user-1",
    phoneNumber: "+375291111111",
    displayName: "Alex",
    photoUrl: null,
    photoAccessExpiresAt: null,
    role: "OWNER",
    joinedAt: "2026-03-20T10:00:00.000Z",
    lastReadAt: null,
    lastSentMessageAt: "2026-03-27T09:00:00.000Z",
    canSendMessages: true,
    canManageMembers: true,
    canManageInviteLinks: true,
    canManageMessages: true,
    canPinMessages: true,
    canApproveJoinRequests: true,
    canPostMessages: true,
    anonymousAdmin: false,
    restrictedUntil: null,
    restrictionReason: null,
    ...overrides
  };
}

function createInviteLink(overrides: Partial<ChatInviteLink> = {}): ChatInviteLink {
  return {
    inviteLinkId: "invite-1",
    chatId: "chat-1",
    label: "Launch",
    token: "invite-token",
    shareUrl: "https://alex.example/invite-token",
    revoked: false,
    usageLimit: 10,
    usageCount: 2,
    expiresAt: null,
    createdAt: "2026-03-27T09:30:00.000Z",
    lastUsedAt: null,
    ...overrides
  };
}

function createJoinRequest(overrides: Partial<ChatJoinRequest> = {}): ChatJoinRequest {
  return {
    userId: "user-3",
    phoneNumber: "+375441112233",
    displayName: "Bob",
    username: "bob",
    photoUrl: null,
    photoAccessExpiresAt: null,
    source: "INVITE_LINK",
    inviteLinkId: "invite-1",
    requestedAt: "2026-03-27T09:35:00.000Z",
    ...overrides
  };
}

function createBan(overrides: Partial<ChatBan> = {}): ChatBan {
  return {
    userId: "user-4",
    phoneNumber: "+375291234567",
    displayName: "Mallory",
    username: "mallory",
    photoUrl: null,
    photoAccessExpiresAt: null,
    bannedAt: "2026-03-27T08:00:00.000Z",
    bannedUntil: null,
    reason: "Spam",
    bannedByUserId: "user-1",
    ...overrides
  };
}

function createAnalytics(overrides: Partial<ChatAnalytics> = {}): ChatAnalytics {
  return {
    chatId: "chat-1",
    chatType: "GROUP",
    memberCount: 12,
    adminCount: 2,
    restrictedCount: 1,
    bannedCount: 1,
    pendingJoinRequestCount: 1,
    activeInviteLinkCount: 2,
    messagesLast24h: 87,
    reactionsLast24h: 25,
    commentsLast24h: 0,
    lastMessageAt: "2026-03-27T10:00:00.000Z",
    ...overrides
  };
}

function createSearchResult(overrides: Partial<UserSearchResult> = {}): UserSearchResult {
  return {
    userId: "user-5",
    phoneNumber: "+375291998877",
    displayName: "Nina",
    username: "nina",
    bot: false,
    botDescription: null,
    botSupportsInline: false,
    botWebAppUrl: null,
    photoUrl: null,
    photoAccessExpiresAt: null,
    online: false,
    lastSeenAt: null,
    ...overrides
  };
}

function setStore(chats: ChatSummary[]) {
  const session = createSession();
  const accountState: AccountState = {
    session,
    featureProfile: null,
    chats,
    folders: [],
    messagesByChat: {},
    lastActivatedAt: "2026-03-27T10:00:00.000Z"
  };

  useAppStore.setState({
    hydrated: true,
    hydrating: false,
    activeAccountId: session.userId,
    accountsById: {
      [session.userId]: accountState
    },
    session,
    featureProfile: null,
    chats,
    folders: [],
    messagesByChat: {}
  });
}

describe("useMembersScreenController", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("loads members and admin datasets for a managed group chat", async () => {
    const chat = createChat();
    const discussionChat = createChat({
      chatId: "chat-discussion",
      title: "Discussion room",
      forumEnabled: false,
      publicUsername: null
    });

    setStore([chat, discussionChat]);

    (api.getChatMembers as jest.Mock).mockResolvedValue([
      createMember(),
      createMember({
        userId: "user-2",
        displayName: "Dana",
        role: "MEMBER",
        canManageMembers: false,
        canManageInviteLinks: false,
        canManageMessages: false,
        canPinMessages: false,
        canApproveJoinRequests: false,
        canPostMessages: false
      })
    ]);
    (api.getChatInviteLinks as jest.Mock).mockResolvedValue([createInviteLink()]);
    (api.getChatJoinRequests as jest.Mock).mockResolvedValue([createJoinRequest()]);
    (api.getChatBans as jest.Mock).mockResolvedValue([createBan()]);
    (api.getChatAnalytics as jest.Mock).mockResolvedValue(createAnalytics());

    const { result } = renderHook(() =>
      useMembersScreenController({
        chat,
        currentUserId: "user-1",
        onClose: jest.fn(),
        token: "token-1"
      })
    );

    await waitFor(() => {
      expect(result.current.loadingMembers).toBe(false);
      expect(result.current.orderedMembers).toHaveLength(2);
      expect(result.current.canManageMembers).toBe(true);
    });

    await waitFor(() => {
      expect(result.current.inviteLinks).toHaveLength(1);
      expect(result.current.joinRequests).toHaveLength(1);
      expect(result.current.bannedMembers).toHaveLength(1);
      expect(result.current.analytics).toEqual(expect.objectContaining({ memberCount: 12 }));
    });

    expect(api.getChatInviteLinks).toHaveBeenCalledWith("token-1", "chat-1");
    expect(api.getChatJoinRequests).toHaveBeenCalledWith("token-1", "chat-1");
    expect(api.getChatBans).toHaveBeenCalledWith("token-1", "chat-1");
    expect(api.getChatAnalytics).toHaveBeenCalledWith("token-1", "chat-1");
    expect(result.current.availableDiscussionChats.map((item) => item.chatId)).toEqual([
      "chat-discussion"
    ]);
  });

  it("searches candidates, filters existing members, and adds selected members", async () => {
    const chat = createChat();
    setStore([chat]);

    (api.getChatMembers as jest.Mock).mockResolvedValue([
      createMember(),
      createMember({
        userId: "user-2",
        displayName: "Dana",
        role: "MEMBER",
        canManageMembers: false,
        canManageInviteLinks: false,
        canManageMessages: false,
        canPinMessages: false,
        canApproveJoinRequests: false,
        canPostMessages: false
      })
    ]);
    (api.getChatInviteLinks as jest.Mock).mockResolvedValue([]);
    (api.getChatJoinRequests as jest.Mock).mockResolvedValue([]);
    (api.getChatBans as jest.Mock).mockResolvedValue([]);
    (api.getChatAnalytics as jest.Mock).mockResolvedValue(createAnalytics());
    (api.searchUsers as jest.Mock).mockResolvedValue([
      createSearchResult({
        userId: "user-2",
        displayName: "Dana"
      }),
      createSearchResult({
        userId: "user-5",
        displayName: "Nina"
      })
    ]);
    (api.addChatMembers as jest.Mock).mockResolvedValue([
      createMember(),
      createMember({
        userId: "user-2",
        displayName: "Dana",
        role: "MEMBER",
        canManageMembers: false,
        canManageInviteLinks: false,
        canManageMessages: false,
        canPinMessages: false,
        canApproveJoinRequests: false,
        canPostMessages: false
      }),
      createMember({
        userId: "user-5",
        displayName: "Nina",
        role: "MEMBER",
        phoneNumber: "+375291998877",
        canManageMembers: false,
        canManageInviteLinks: false,
        canManageMessages: false,
        canPinMessages: false,
        canApproveJoinRequests: false,
        canPostMessages: false
      })
    ]);

    const { result } = renderHook(() =>
      useMembersScreenController({
        chat,
        currentUserId: "user-1",
        onClose: jest.fn(),
        token: "token-1"
      })
    );

    await waitFor(() => {
      expect(result.current.canManageMembers).toBe(true);
    });

    act(() => {
      result.current.setQuery("ni");
    });

    await waitFor(() => {
      expect(api.searchUsers).toHaveBeenCalledWith("token-1", "ni");
      expect(result.current.results.map((item) => item.userId)).toEqual(["user-5"]);
    });

    act(() => {
      result.current.toggleCandidate("user-5");
    });

    await act(async () => {
      await result.current.handleAddMembers();
    });

    expect(api.addChatMembers).toHaveBeenCalledWith("token-1", "chat-1", ["user-5"]);
    await waitFor(() => {
      expect(result.current.orderedMembers).toHaveLength(3);
      expect(result.current.selectedUserIds).toEqual([]);
      expect(result.current.query).toBe("");
      expect(result.current.results).toEqual([]);
    });
  });

  it("updates archived and muted chat state through the shared store", async () => {
    const chat = createChat();
    setStore([chat]);

    (api.getChatMembers as jest.Mock).mockResolvedValue([createMember()]);
    (api.getChatInviteLinks as jest.Mock).mockResolvedValue([]);
    (api.getChatJoinRequests as jest.Mock).mockResolvedValue([]);
    (api.getChatBans as jest.Mock).mockResolvedValue([]);
    (api.getChatAnalytics as jest.Mock).mockResolvedValue(createAnalytics());

    const onChatUpdated = jest.fn();
    const archivedChat = createChat({
      archived: true,
      title: "Team"
    });
    const mutedChat = createChat({
      mutedUntil: "2026-03-28T10:00:00.000Z"
    });

    (api.setChatArchived as jest.Mock).mockResolvedValue(archivedChat);
    (api.muteChat as jest.Mock).mockResolvedValue(mutedChat);

    const { result } = renderHook(() =>
      useMembersScreenController({
        chat,
        currentUserId: "user-1",
        onChatUpdated,
        onClose: jest.fn(),
        token: "token-1"
      })
    );

    await waitFor(() => {
      expect(result.current.loadingMembers).toBe(false);
    });

    await act(async () => {
      await result.current.handleArchiveToggle();
    });

    expect(api.setChatArchived).toHaveBeenCalledWith("token-1", "chat-1", true);
    await waitFor(() => {
      expect(useAppStore.getState().chats[0]?.archived).toBe(true);
      expect(result.current.notice).toBe("Chat archived.");
    });

    await act(async () => {
      await result.current.handleMuteToggle();
    });

    expect(api.muteChat).toHaveBeenCalledWith("token-1", "chat-1", expect.any(String));
    await waitFor(() => {
      expect(useAppStore.getState().chats[0]?.mutedUntil).toBe("2026-03-28T10:00:00.000Z");
      expect(result.current.notice).toBe("Chat muted for 24 hours.");
    });

    expect(onChatUpdated).toHaveBeenCalledWith(expect.objectContaining({ archived: true }));
    expect(onChatUpdated).toHaveBeenCalledWith(
      expect.objectContaining({ mutedUntil: "2026-03-28T10:00:00.000Z" })
    );
  });
});
