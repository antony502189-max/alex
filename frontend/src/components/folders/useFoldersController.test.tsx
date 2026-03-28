jest.mock("../../services/api", () => ({
  api: {
    createFolder: jest.fn(),
    deleteFolder: jest.fn(),
    getFolders: jest.fn(),
    updateFolder: jest.fn()
  }
}));

jest.mock("../../services/localDatabase", () => ({
  localDatabase: {
    getFolders: jest.fn(),
    replaceFolders: jest.fn()
  }
}));

import { act, renderHook, waitFor } from "@testing-library/react-native";
import { api } from "../../services/api";
import { localDatabase } from "../../services/localDatabase";
import { useAppStore } from "../../store/useAppStore";
import type { ChatFolder, ChatSummary } from "../../types";
import { useFoldersController } from "./useFoldersController";

function createFolder(overrides: Partial<ChatFolder> = {}): ChatFolder {
  return {
    chatIds: ["chat-1"],
    folderId: "folder-1",
    position: 0,
    title: "Work",
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
    title: "Team",
    topicCount: 0,
    unreadCount: 0,
    ...overrides
  };
}

describe("useFoldersController", () => {
  beforeEach(() => {
    useAppStore.setState({
      activeAccountId: "user-1",
      accountsById: {},
      chats: [createChat(), createChat({ chatId: "chat-2", title: "Saved", chatType: "SAVED" })],
      folders: [],
      hydrated: true,
      hydrating: false,
      messagesByChat: {},
      session: {
        accessTokenExpiresAt: null,
        authMethod: "OTP",
        displayName: "Alex",
        phoneNumber: "+375291234567",
        refreshToken: "refresh-1",
        refreshTokenExpiresAt: null,
        sessionId: "session-1",
        token: "token-1",
        trustedSession: true,
        userId: "user-1",
        username: "alex"
      }
    });
    jest.clearAllMocks();
    (localDatabase.getFolders as jest.Mock).mockResolvedValue([]);
    (localDatabase.replaceFolders as jest.Mock).mockResolvedValue(undefined);
  });

  it("loads folders and hydrates editor state", async () => {
    (api.getFolders as jest.Mock).mockResolvedValue([createFolder()]);

    const { result } = renderHook(() => useFoldersController({ token: "token-1" }));

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
      expect(result.current.folders).toHaveLength(1);
    });

    act(() => {
      result.current.handleSelectFolder("folder-1");
    });

    expect(result.current.title).toBe("Work");
    expect(result.current.selectedChatIds).toEqual(["chat-1"]);
  });

  it("creates and deletes folders", async () => {
    (api.getFolders as jest.Mock).mockResolvedValue([]);
    (api.createFolder as jest.Mock).mockResolvedValue(
      createFolder({ chatIds: ["chat-1", "chat-2"], title: "Pinned" })
    );
    (api.deleteFolder as jest.Mock).mockResolvedValue([]);

    const { result } = renderHook(() => useFoldersController({ token: "token-1" }));

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
    });

    act(() => {
      result.current.setTitle("Pinned");
      result.current.toggleChat("chat-1");
      result.current.toggleChat("chat-2");
    });

    await act(async () => {
      await result.current.handleSave();
    });

    expect(api.createFolder).toHaveBeenCalledWith("token-1", {
      chatIds: ["chat-1", "chat-2"],
      position: 0,
      title: "Pinned"
    });

    await act(async () => {
      await result.current.handleDelete();
    });

    expect(api.deleteFolder).toHaveBeenCalledWith("token-1", "folder-1");
  });
});
