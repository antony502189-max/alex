jest.mock("../../services/api", () => ({
  api: {
    getChats: jest.fn(),
    getFolders: jest.fn(),
    revokeSession: jest.fn()
  }
}));

jest.mock("../../services/localDatabase", () => ({
  localDatabase: {
    getChats: jest.fn(async () => []),
    getFolders: jest.fn(async () => []),
    purgeAccountData: jest.fn(async () => undefined),
    replaceChats: jest.fn(async () => undefined),
    replaceFolders: jest.fn(async () => undefined)
  }
}));

import { act, renderHook, waitFor } from "@testing-library/react-native";
import { api } from "../../services/api";
import { useAppStore } from "../../store/useAppStore";
import type { ChatFolder, ChatSummary } from "../../types";
import { useChatsListController } from "./useChatsListController";

function createChat(overrides: Partial<ChatSummary> = {}): ChatSummary {
  return {
    about: null,
    archived: false,
    autoDeleteSeconds: null,
    chatId: "chat-1",
    chatType: "DIRECT",
    commentsEnabled: true,
    crossPostingEnabled: false,
    draftText: null,
    draftUpdatedAt: null,
    forumEnabled: false,
    joinRequiresApproval: false,
    lastMessageAt: "2026-03-27T10:00:00.000Z",
    lastReadAt: null,
    linkedDiscussionChatId: null,
    linkedDiscussionChatTitle: null,
    markedUnread: false,
    memberCount: 2,
    mentionCount: 0,
    mutedUntil: null,
    peerBotSupportsInline: false,
    peerBotWebAppUrl: null,
    peerDisplayName: "Alex Doe",
    peerIsBot: false,
    peerLastSeenAt: null,
    peerOnline: false,
    peerPhoneNumber: "+375291234567",
    peerUserId: "user-2",
    photoAccessExpiresAt: null,
    photoUrl: null,
    pinOrder: null,
    pinned: false,
    pinnedMessageId: null,
    publicUsername: null,
    reactionsEnabled: true,
    replyCount: 0,
    slowModeSeconds: null,
    title: "Alex Doe",
    topicCount: 0,
    unreadCount: 0,
    ...overrides
  };
}

describe("useChatsListController", () => {
  beforeEach(() => {
    useAppStore.setState({
      hydrated: true,
      hydrating: false,
      activeAccountId: "user-1",
      accountsById: {
        "user-1": {
          session: {
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
          },
          featureProfile: null,
          chats: [],
          folders: [],
          messagesByChat: {},
          lastActivatedAt: new Date().toISOString()
        }
      },
      session: {
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
      },
      featureProfile: null,
      chats: [],
      folders: [],
      messagesByChat: {}
    });
  });

  it("loads chats, derives counts, and applies filters", async () => {
    const designChat = createChat({
      about: "Core design",
      chatId: "chat-design",
      chatType: "GROUP",
      memberCount: 4,
      title: "Design team",
      unreadCount: 3
    });
    const botChat = createChat({
      chatId: "chat-bot",
      peerBotSupportsInline: true,
      peerDisplayName: "Helper Bot",
      peerIsBot: true,
      title: "Helper Bot"
    });
    const folders: ChatFolder[] = [
      {
        folderId: "folder-work",
        title: "Work",
        position: 0,
        chatIds: ["chat-design"]
      }
    ];

    (api.getChats as jest.Mock).mockResolvedValue([designChat, botChat]);
    (api.getFolders as jest.Mock).mockResolvedValue(folders);

    const { result } = renderHook(() =>
      useChatsListController({
        featureFlags: { calls: false, stories: true },
        onCreateDirect: jest.fn(),
        onOpenCalls: jest.fn(),
        onOpenContacts: jest.fn(),
        onOpenProfile: jest.fn(),
        onOpenSavedMessages: jest.fn(),
        onOpenStories: jest.fn()
      })
    );

    await waitFor(() => {
      expect(api.getChats).toHaveBeenCalledWith("token-1");
      expect(result.current.displayedChats).toHaveLength(2);
    });

    expect(result.current.unreadChatsCount).toBe(1);
    expect(result.current.unreadMessagesCount).toBe(3);
    expect(result.current.directChatsCount).toBe(1);
    expect(result.current.quickActions.some((action) => action.key === "calls")).toBe(false);
    expect(result.current.quickActions.some((action) => action.key === "stories")).toBe(true);

    act(() => {
      result.current.setSelectedFilter("UNREAD");
    });
    expect(result.current.displayedChats.map((chat) => chat.chatId)).toEqual(["chat-design"]);

    act(() => {
      result.current.setSelectedFilter("ALL");
      result.current.setSelectedFolderId("folder-work");
    });
    expect(result.current.displayedChats.map((chat) => chat.chatId)).toEqual(["chat-design"]);

    act(() => {
      result.current.setSelectedFolderId(null);
      result.current.setSearchQuery("helper");
    });
    expect(result.current.displayedChats.map((chat) => chat.chatId)).toEqual(["chat-bot"]);
  });

  it("revokes the active session on logout", async () => {
    (api.getChats as jest.Mock).mockResolvedValue([]);
    (api.getFolders as jest.Mock).mockResolvedValue([]);
    (api.revokeSession as jest.Mock).mockResolvedValue(undefined);

    const { result } = renderHook(() =>
      useChatsListController({
        onCreateDirect: jest.fn(),
        onOpenCalls: jest.fn(),
        onOpenContacts: jest.fn(),
        onOpenProfile: jest.fn(),
        onOpenSavedMessages: jest.fn(),
        onOpenStories: jest.fn()
      })
    );

    await act(async () => {
      await result.current.handleLogout();
    });

    expect(api.revokeSession).toHaveBeenCalledWith("token-1", "session-1");
    expect(useAppStore.getState().session).toBeNull();
  });
});
