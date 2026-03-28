jest.mock("../../services/api", () => ({
  api: {
    createDirectChat: jest.fn(),
    searchGlobal: jest.fn()
  }
}));

import { act, renderHook, waitFor } from "@testing-library/react-native";
import { api } from "../../services/api";
import type {
  ChatSummary,
  GlobalSearchResponse,
  UserSearchResult
} from "../../types";
import { useGlobalSearchController } from "./useGlobalSearchController";

function createUser(overrides: Partial<UserSearchResult> = {}): UserSearchResult {
  return {
    userId: "user-1",
    phoneNumber: "+375291111111",
    displayName: "Alex",
    username: "alex",
    bot: false,
    botDescription: null,
    botSupportsInline: false,
    botWebAppUrl: null,
    photoUrl: null,
    photoAccessExpiresAt: null,
    online: true,
    lastSeenAt: null,
    ...overrides
  };
}

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
    peerDisplayName: "Alex",
    peerIsBot: false,
    peerLastSeenAt: null,
    peerOnline: true,
    peerPhoneNumber: "+375291111111",
    peerUserId: "user-1",
    photoAccessExpiresAt: null,
    photoUrl: null,
    pinOrder: null,
    pinned: false,
    pinnedMessageId: null,
    publicUsername: null,
    reactionsEnabled: true,
    replyCount: 0,
    slowModeSeconds: null,
    title: "Alex",
    topicCount: 0,
    unreadCount: 0,
    ...overrides
  };
}

function createResults(): GlobalSearchResponse {
  return {
    query: "al",
    users: [createUser()],
    chats: [createChat({ chatType: "GROUP", chatId: "chat-2", title: "Team" })],
    messages: []
  };
}

describe("useGlobalSearchController", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("searches globally after debounce and derives summary state", async () => {
    jest.useFakeTimers();
    (api.searchGlobal as jest.Mock).mockResolvedValue(createResults());

    const { result } = renderHook(() =>
      useGlobalSearchController({
        availableChats: [],
        onOpenChat: jest.fn(),
        token: "token-1"
      })
    );

    act(() => {
      result.current.setQuery("al");
      jest.advanceTimersByTime(300);
    });

    await waitFor(() => {
      expect(api.searchGlobal).toHaveBeenCalledWith("token-1", "al", 12);
      expect(result.current.hasResults).toBe(true);
      expect(result.current.resultSummary).toBe("2 results");
    });

    jest.useRealTimers();
  });

  it("opens a direct chat from a user result", async () => {
    (api.createDirectChat as jest.Mock).mockResolvedValue(createChat());
    const onOpenChat = jest.fn();

    const { result } = renderHook(() =>
      useGlobalSearchController({
        availableChats: [],
        onOpenChat,
        token: "token-1"
      })
    );

    await act(async () => {
      await result.current.handleOpenUser(createUser());
    });

    expect(api.createDirectChat).toHaveBeenCalledWith("token-1", "user-1");
    expect(onOpenChat).toHaveBeenCalledWith(expect.objectContaining({ chatId: "chat-1" }));
  });

  it("recognizes pasted links and skips global search requests", async () => {
    jest.useFakeTimers();

    const { result } = renderHook(() =>
      useGlobalSearchController({
        availableChats: [],
        onOpenChat: jest.fn(),
        token: "token-1"
      })
    );

    act(() => {
      result.current.setQuery("https://t.me/+invite-token");
      jest.advanceTimersByTime(300);
    });

    expect(result.current.parsedLink).toEqual({
      type: "JOIN",
      token: "invite-token"
    });
    expect(api.searchGlobal).not.toHaveBeenCalled();

    jest.useRealTimers();
  });

  it("resolves exact local public chat matches for parsed username links", () => {
    const { result } = renderHook(() =>
      useGlobalSearchController({
        availableChats: [
          createChat({
            about: null,
            chatId: "chat-team",
            chatType: "GROUP",
            peerDisplayName: null,
            peerPhoneNumber: null,
            peerUserId: null,
            publicUsername: "team",
            title: "Team"
          })
        ],
        onOpenChat: jest.fn(),
        token: "token-1"
      })
    );

    act(() => {
      result.current.setQuery("@team");
    });

    expect(result.current.parsedLink).toEqual({
      type: "JOIN",
      token: "@team"
    });
    expect(result.current.exactPublicChatMatch).toEqual(
      expect.objectContaining({
        chatId: "chat-team"
      })
    );
  });
});
