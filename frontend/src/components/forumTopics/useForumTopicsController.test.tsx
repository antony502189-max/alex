jest.mock("../../services/api", () => ({
  api: {
    createForumTopic: jest.fn(),
    getForumTopics: jest.fn(),
    updateForumTopic: jest.fn()
  }
}));

jest.mock("../../services/localDatabase", () => ({
  localDatabase: {
    getForumTopics: jest.fn(),
    replaceForumTopics: jest.fn(),
    upsertForumTopics: jest.fn()
  }
}));

import { act, renderHook, waitFor } from "@testing-library/react-native";
import { api } from "../../services/api";
import { localDatabase } from "../../services/localDatabase";
import type { ChatSummary, ForumTopic } from "../../types";
import { useForumTopicsController } from "./useForumTopicsController";

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
    forumEnabled: true,
    joinRequiresApproval: false,
    lastMessageAt: "2026-03-27T10:00:00.000Z",
    lastReadAt: null,
    linkedDiscussionChatId: null,
    linkedDiscussionChatTitle: null,
    markedUnread: false,
    memberCount: 5,
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
    topicCount: 2,
    unreadCount: 0,
    ...overrides
  };
}

function createTopic(overrides: Partial<ForumTopic> = {}): ForumTopic {
  return {
    topicId: "topic-1",
    chatId: "chat-1",
    title: "Launch",
    iconEmoji: "🔥",
    generalTopic: false,
    closed: false,
    hidden: false,
    createdBy: "user-1",
    createdAt: "2026-03-27T10:00:00.000Z",
    updatedAt: "2026-03-27T10:00:00.000Z",
    lastMessageAt: "2026-03-27T10:30:00.000Z",
    ...overrides
  };
}

describe("useForumTopicsController", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("loads cached topics and then replaces them with fresh server data", async () => {
    (localDatabase.getForumTopics as jest.Mock).mockResolvedValue([createTopic({ topicId: "cached-1" })]);
    (api.getForumTopics as jest.Mock).mockResolvedValue([createTopic({ topicId: "remote-1" })]);

    const { result } = renderHook(() =>
      useForumTopicsController({
        chat: createChat(),
        currentUserId: "user-1",
        token: "token-1"
      })
    );

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
      expect(result.current.topics[0]?.topicId).toBe("remote-1");
    });

    expect(localDatabase.replaceForumTopics).toHaveBeenCalledWith("user-1", "chat-1", [
      expect.objectContaining({ topicId: "remote-1" })
    ]);
  });

  it("creates topics and toggles topic closed state", async () => {
    (localDatabase.getForumTopics as jest.Mock).mockResolvedValue([]);
    (api.getForumTopics as jest.Mock).mockResolvedValue([createTopic()]);
    (api.createForumTopic as jest.Mock).mockResolvedValue(createTopic({ topicId: "topic-2", title: "Roadmap" }));
    (api.updateForumTopic as jest.Mock).mockResolvedValue(createTopic({ topicId: "topic-1", closed: true }));

    const { result } = renderHook(() =>
      useForumTopicsController({
        chat: createChat(),
        currentUserId: "user-1",
        onRefreshChats: jest.fn(),
        token: "token-1"
      })
    );

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
    });

    act(() => {
      result.current.setTitle("Roadmap");
      result.current.setIconEmoji("🗺️");
    });

    await act(async () => {
      await result.current.handleCreateTopic();
    });

    expect(api.createForumTopic).toHaveBeenCalledWith("token-1", "chat-1", {
      title: "Roadmap",
      iconEmoji: "🗺️"
    });

    await act(async () => {
      await result.current.handleToggleTopicClosed(createTopic());
    });

    expect(api.updateForumTopic).toHaveBeenCalledWith("token-1", "chat-1", "topic-1", {
      closed: true
    });
  });
});
