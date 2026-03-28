import type { ChatFolder, ChatSummary } from "../../types";
import {
  buildFolderEmptyState,
  buildFolderSaveLabel,
  formatFolderChatMeta,
  sortFoldersByPosition
} from "./foldersPresentation";

function createFolder(overrides: Partial<ChatFolder> = {}): ChatFolder {
  return {
    chatIds: [],
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

describe("foldersPresentation", () => {
  it("sorts folders by position", () => {
    expect(
      sortFoldersByPosition([
        createFolder({ folderId: "b", position: 2 }),
        createFolder({ folderId: "a", position: 1 })
      ]).map((folder) => folder.folderId)
    ).toEqual(["a", "b"]);
  });

  it("formats chat metadata and labels", () => {
    expect(formatFolderChatMeta(createChat({ chatType: "DIRECT", peerPhoneNumber: "+375291111111" }))).toBe(
      "+375291111111"
    );
    expect(formatFolderChatMeta(createChat({ chatType: "SAVED" }))).toBe("private notes");
    expect(formatFolderChatMeta(createChat({ forumEnabled: true, topicCount: 3 }))).toContain("3 topics");
    expect(
      formatFolderChatMeta(
        createChat({
          chatType: "CHANNEL",
          memberCount: 1200,
          title: "News"
        })
      )
    ).toBe("1200 subscribers");
    expect(buildFolderSaveLabel(true)).toBe("Saving...");
    expect(buildFolderEmptyState()).toContain("Select chats");
  });
});
