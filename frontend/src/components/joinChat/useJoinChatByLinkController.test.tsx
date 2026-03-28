jest.mock("../../services/api", () => ({
  api: {
    joinChatByLink: jest.fn(),
    joinChatByUsername: jest.fn(),
    searchPublicChats: jest.fn()
  }
}));

import { act, renderHook, waitFor } from "@testing-library/react-native";
import { api } from "../../services/api";
import type {
  ChatSummary,
  PublicChatDiscovery
} from "../../types";
import { useJoinChatByLinkController } from "./useJoinChatByLinkController";

function createChat(overrides: Partial<ChatSummary> = {}): ChatSummary {
  return {
    about: null,
    archived: false,
    autoDeleteSeconds: null,
    chatId: "chat-1",
    chatType: "GROUP",
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
    memberCount: 3,
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
    chatId: "chat-1",
    chatType: "GROUP",
    title: "Team",
    photoUrl: null,
    photoAccessExpiresAt: null,
    publicUsername: "team",
    about: null,
    forumEnabled: false,
    memberCount: 12,
    joinRequiresApproval: true,
    joined: false,
    ...overrides
  };
}

describe("useJoinChatByLinkController", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("discovers public chats after debounce", async () => {
    (api.searchPublicChats as jest.Mock).mockResolvedValue([createDiscovery()]);

    const { result } = renderHook(() =>
      useJoinChatByLinkController({
        availableChats: [],
        onJoined: jest.fn(),
        onOpenDiscoveryChat: jest.fn(),
        token: "token-1"
      })
    );

    act(() => {
      result.current.handleInviteTokenChange("@te");
    });

    await waitFor(() => {
      expect(api.searchPublicChats).toHaveBeenCalledWith("token-1", "te", 8);
      expect(result.current.discoveries).toHaveLength(1);
    });
  });

  it("normalizes tg-resolve and scheme-less public links for discovery and join", async () => {
    const onJoined = jest.fn();
    (api.searchPublicChats as jest.Mock).mockResolvedValue([createDiscovery()]);
    (api.joinChatByUsername as jest.Mock).mockResolvedValue({
      status: "JOINED",
      chat: createChat(),
      chatId: "chat-1",
      title: "Team",
      publicUsername: "team",
      requestedAt: null
    });

    const { result } = renderHook(() =>
      useJoinChatByLinkController({
        availableChats: [],
        onJoined,
        onOpenDiscoveryChat: jest.fn(),
        token: "token-1"
      })
    );

    act(() => {
      result.current.handleInviteTokenChange("tg://resolve?domain=team");
    });

    await waitFor(() => {
      expect(api.searchPublicChats).toHaveBeenCalledWith("token-1", "team", 8);
    });

    act(() => {
      result.current.handleInviteTokenChange("t.me/team");
    });

    await act(async () => {
      await result.current.handleJoin();
    });

    expect(api.joinChatByUsername).toHaveBeenCalledWith("token-1", "@team");
    expect(onJoined).toHaveBeenCalledWith(expect.objectContaining({ chatId: "chat-1" }));
  });

  it("joins by link and by discovered public username", async () => {
    const onJoined = jest.fn();
    (api.joinChatByLink as jest.Mock).mockResolvedValue({
      status: "REQUESTED",
      chat: null,
      chatId: "chat-1",
      title: "Team",
      publicUsername: "team",
      requestedAt: "2026-03-27T12:00:00.000Z"
    });
    (api.joinChatByUsername as jest.Mock).mockResolvedValue({
      status: "JOINED",
      chat: createChat(),
      chatId: "chat-1",
      title: "Team",
      publicUsername: "team",
      requestedAt: null
    });

    const { result } = renderHook(() =>
      useJoinChatByLinkController({
        availableChats: [],
        initialInviteToken: "alex://join/team",
        onJoined,
        onOpenDiscoveryChat: jest.fn(),
        token: "token-1"
      })
    );

    await act(async () => {
      await result.current.handleJoin();
    });

    expect(api.joinChatByLink).toHaveBeenCalledWith("token-1", "team");
    expect(result.current.statusMessage).toBe("Join request sent to Team (@team).");

    await act(async () => {
      await result.current.handleJoinDiscoveredChat(createDiscovery());
    });

    expect(api.joinChatByUsername).toHaveBeenCalledWith("token-1", "@team");
    expect(onJoined).toHaveBeenCalledWith(expect.objectContaining({ chatId: "chat-1" }));
  });

  it("treats parsed call links as quick actions instead of join targets", async () => {
    const { result } = renderHook(() =>
      useJoinChatByLinkController({
        availableChats: [],
        initialInviteToken: "tg://call/room-77",
        onJoined: jest.fn(),
        onOpenDiscoveryChat: jest.fn(),
        token: "token-1"
      })
    );

    expect(result.current.parsedLink).toEqual({
      type: "CALL",
      token: "room-77"
    });
    expect(result.current.canJoin).toBe(false);
    expect(api.searchPublicChats).not.toHaveBeenCalled();

    await act(async () => {
      await result.current.handleJoin();
    });

    expect(api.joinChatByLink).not.toHaveBeenCalled();
    expect(api.joinChatByUsername).not.toHaveBeenCalled();
  });

  it("opens an already joined discovered chat instead of trying to join it again", async () => {
    const onOpenDiscoveryChat = jest.fn();

    const { result } = renderHook(() =>
      useJoinChatByLinkController({
        availableChats: [],
        onJoined: jest.fn(),
        onOpenDiscoveryChat,
        token: "token-1"
      })
    );

    await act(async () => {
      await result.current.handleJoinDiscoveredChat(
        createDiscovery({
          chatId: "chat-joined",
          joined: true,
          publicUsername: null
        })
      );
    });

    expect(onOpenDiscoveryChat).toHaveBeenCalledWith("chat-joined");
    expect(api.joinChatByLink).not.toHaveBeenCalled();
    expect(api.joinChatByUsername).not.toHaveBeenCalled();
  });

  it("exposes a local exact public-chat match and opens it directly", () => {
    const onOpenDiscoveryChat = jest.fn();
    const exactPublicChatMatch = createChat({
      chatId: "chat-local",
      publicUsername: "team"
    });

    const { result } = renderHook(() =>
      useJoinChatByLinkController({
        availableChats: [exactPublicChatMatch],
        onJoined: jest.fn(),
        onOpenDiscoveryChat,
        token: "token-1"
      })
    );

    act(() => {
      result.current.handleInviteTokenChange("@team");
    });

    expect(result.current.exactPublicChatMatch).toEqual(exactPublicChatMatch);

    act(() => {
      result.current.handleOpenExactPublicChat();
    });

    expect(onOpenDiscoveryChat).toHaveBeenCalledWith("chat-local");
    expect(api.joinChatByLink).not.toHaveBeenCalled();
    expect(api.joinChatByUsername).not.toHaveBeenCalled();
  });

  it("skips public discovery requests when the parsed username already matches a local chat", () => {
    const { result, unmount } = renderHook(() =>
      useJoinChatByLinkController({
        availableChats: [
          createChat({
            chatId: "chat-local",
            publicUsername: "team"
          })
        ],
        onJoined: jest.fn(),
        onOpenDiscoveryChat: jest.fn(),
        token: "token-1"
      })
    );

    act(() => {
      result.current.handleInviteTokenChange("@team");
    });

    expect(result.current.exactPublicChatMatch).toEqual(
      expect.objectContaining({
        chatId: "chat-local"
      })
    );
    expect(result.current.discoveries).toEqual([]);
    expect(api.searchPublicChats).not.toHaveBeenCalled();

    unmount();
  });
});
