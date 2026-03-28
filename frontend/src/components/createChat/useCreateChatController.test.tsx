jest.mock("../../services/api", () => ({
  api: {
    createChannel: jest.fn(),
    createDirectChat: jest.fn(),
    createGroupChat: jest.fn(),
    searchUsers: jest.fn()
  }
}));

import { act, renderHook, waitFor } from "@testing-library/react-native";
import { api } from "../../services/api";
import type { ChatSummary, UserSearchResult } from "../../types";
import { useCreateChatController } from "./useCreateChatController";

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
    peerOnline: false,
    peerPhoneNumber: "+375291111111",
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
    title: "Alex",
    topicCount: 0,
    unreadCount: 0,
    ...overrides
  };
}

function createUser(overrides: Partial<UserSearchResult> = {}): UserSearchResult {
  return {
    userId: "user-2",
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

describe("useCreateChatController", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("searches users after debounce and opens a direct chat", async () => {
    jest.useFakeTimers();
    (api.searchUsers as jest.Mock).mockResolvedValue([createUser()]);
    (api.createDirectChat as jest.Mock).mockResolvedValue(createChat());
    const onCreated = jest.fn();

    const { result } = renderHook(() =>
      useCreateChatController({
        mode: "direct",
        onCreated,
        token: "token-1"
      })
    );

    act(() => {
      result.current.setQuery("al");
      jest.advanceTimersByTime(300);
    });

    await waitFor(() => {
      expect(api.searchUsers).toHaveBeenCalledWith("token-1", "al");
      expect(result.current.results).toHaveLength(1);
    });

    await act(async () => {
      await result.current.handleSelectDirect("user-2");
    });

    expect(api.createDirectChat).toHaveBeenCalledWith("token-1", "user-2");
    expect(onCreated).toHaveBeenCalledWith(expect.objectContaining({ chatId: "chat-1" }));
    jest.useRealTimers();
  });

  it("creates a group chat with selected users and settings", async () => {
    jest.useFakeTimers();
    (api.searchUsers as jest.Mock).mockResolvedValue([
      createUser(),
      createUser({
        userId: "user-3",
        displayName: "Dana",
        username: "dana"
      })
    ]);
    (api.createGroupChat as jest.Mock).mockResolvedValue(
      createChat({
        chatId: "group-1",
        chatType: "GROUP",
        memberCount: 3,
        title: "Core team"
      })
    );
    const onCreated = jest.fn();

    const { result } = renderHook(() =>
      useCreateChatController({
        mode: "group",
        onCreated,
        token: "token-1"
      })
    );

    act(() => {
      result.current.setGroupTitle(" Core team ");
      result.current.setGroupAbout(" Product builders ");
      result.current.setAutoDeleteSeconds("3600");
      result.current.setForumEnabled(true);
      result.current.setJoinRequiresApproval(true);
      result.current.setQuery("da");
      jest.advanceTimersByTime(300);
    });

    await waitFor(() => {
      expect(result.current.results).toHaveLength(2);
    });

    act(() => {
      result.current.toggleUser("user-2");
      result.current.toggleUser("user-3");
    });

    await act(async () => {
      await result.current.handleCreateCollectionChat();
    });

    expect(api.createGroupChat).toHaveBeenCalledWith("token-1", {
      title: "Core team",
      about: "Product builders",
      autoDeleteSeconds: 3600,
      forumEnabled: true,
      joinRequiresApproval: true,
      memberIds: ["user-2", "user-3"]
    });
    expect(onCreated).toHaveBeenCalledWith(expect.objectContaining({ chatId: "group-1" }));
    jest.useRealTimers();
  });
});
