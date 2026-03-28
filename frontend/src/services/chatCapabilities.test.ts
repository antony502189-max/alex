import type { ChatSummary } from "../types";
import { canStartCallsFromChat } from "./chatCapabilities";

function createChat(overrides: Partial<ChatSummary> = {}): ChatSummary {
  return {
    about: null,
    archived: false,
    autoDeleteSeconds: null,
    chatId: "chat-1",
    chatType: "DIRECT",
    commentsEnabled: false,
    crossPostingEnabled: false,
    draftText: null,
    draftUpdatedAt: null,
    forumEnabled: false,
    joinRequiresApproval: false,
    lastMessageAt: "2026-03-28T10:00:00.000Z",
    lastReadAt: null,
    linkedDiscussionChatId: null,
    linkedDiscussionChatTitle: null,
    markedUnread: false,
    memberCount: 2,
    mentionCount: 0,
    mutedUntil: null,
    peerBotSupportsInline: false,
    peerBotWebAppUrl: null,
    peerDisplayName: "Alice",
    peerIsBot: false,
    peerLastSeenAt: null,
    peerOnline: false,
    peerPhoneNumber: null,
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
    title: "Alice",
    topicCount: 0,
    unreadCount: 0,
    ...overrides
  };
}

describe("canStartCallsFromChat", () => {
  it("allows regular chats but blocks saved messages and direct bot chats", () => {
    expect(canStartCallsFromChat(createChat())).toBe(true);
    expect(canStartCallsFromChat(createChat({ chatType: "GROUP", title: "Team" }))).toBe(true);
    expect(
      canStartCallsFromChat(createChat({ chatType: "SAVED", title: "Saved Messages" }))
    ).toBe(false);
    expect(
      canStartCallsFromChat(
        createChat({
          peerDisplayName: "Gif Bot",
          peerIsBot: true,
          title: "Gif Bot"
        })
      )
    ).toBe(false);
  });
});
