import type { ChatSummary } from "../../types";
import {
  buildArchivedChatMeta,
  buildArchivedChatPreview,
  formatArchivedChatAutoDelete
} from "./archivedChatsPresentation";

function createChat(overrides: Partial<ChatSummary> = {}): ChatSummary {
  return {
    about: "General coordination",
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
    publicUsername: "teamroom",
    reactionsEnabled: true,
    replyCount: 0,
    slowModeSeconds: null,
    title: "Team Room",
    topicCount: 4,
    unreadCount: 0,
    ...overrides
  };
}

describe("archivedChatsPresentation", () => {
  it("formats ttl labels and chat meta", () => {
    expect(formatArchivedChatAutoDelete(45)).toBe("TTL 45s");
    expect(formatArchivedChatAutoDelete(1800)).toBe("TTL 30m");
    expect(formatArchivedChatAutoDelete(7200)).toBe("TTL 2h");
    expect(buildArchivedChatMeta(createChat({ forumEnabled: true }))).toBe("@teamroom | 12 members | 4 topics");
    expect(
      buildArchivedChatMeta(
        createChat({
          chatType: "CHANNEL",
          forumEnabled: false,
          memberCount: 1200,
          publicUsername: "news",
          title: "News"
        })
      )
    ).toBe("@news | 1200 subscribers");
  });

  it("builds previews for drafts and direct chats", () => {
    expect(buildArchivedChatPreview(createChat({ draftText: "Reply later" }))).toBe("Draft: Reply later");
    expect(buildArchivedChatMeta(createChat({ chatType: "DIRECT", peerPhoneNumber: "+375291234567" }))).toBe("+375291234567");
    expect(buildArchivedChatMeta(createChat({ chatType: "SAVED" }))).toBe("private notes");
  });
});
