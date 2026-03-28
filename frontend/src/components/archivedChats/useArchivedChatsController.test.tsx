jest.mock("../../services/api", () => ({
  api: {
    getArchivedChats: jest.fn()
  }
}));

import { renderHook, waitFor } from "@testing-library/react-native";
import { api } from "../../services/api";
import type { ChatSummary } from "../../types";
import { useArchivedChatsController } from "./useArchivedChatsController";

function createChat(overrides: Partial<ChatSummary> = {}): ChatSummary {
  return {
    about: null,
    archived: true,
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
    publicUsername: null,
    reactionsEnabled: true,
    replyCount: 0,
    slowModeSeconds: null,
    title: "Team Room",
    topicCount: 0,
    unreadCount: 0,
    ...overrides
  };
}

describe("useArchivedChatsController", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("loads archived chats", async () => {
    (api.getArchivedChats as jest.Mock).mockResolvedValue([createChat()]);

    const { result } = renderHook(() =>
      useArchivedChatsController({
        token: "token-1"
      })
    );

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
      expect(result.current.chats).toHaveLength(1);
    });

    expect(api.getArchivedChats).toHaveBeenCalledWith("token-1");
  });
});
