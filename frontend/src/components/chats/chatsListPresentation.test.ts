import type { ChatSummary } from "../../types";
import {
  buildChatFilterOptions,
  buildChatsFeatureFlags,
  formatAutoDelete,
  formatChatMeta,
  matchesFilter,
  matchesSearch,
  summarizeUnread
} from "./chatsListPresentation";

function createChat(overrides: Partial<ChatSummary> = {}): ChatSummary {
  return {
    about: null,
    archived: false,
    autoDeleteSeconds: null,
    chatId: "chat-1",
    chatType: "DIRECT",
    commentsEnabled: true,
    crossPostingEnabled: false,
    draftText: "",
    draftUpdatedAt: null,
    forumEnabled: false,
    lastMessageAt: "2026-03-27T10:00:00.000Z",
    lastReadAt: null,
    linkedDiscussionChatId: null,
    linkedDiscussionChatTitle: null,
    joinRequiresApproval: false,
    markedUnread: false,
    memberCount: 2,
    mentionCount: 0,
    mutedUntil: null,
    photoAccessExpiresAt: null,
    peerBotSupportsInline: false,
    peerBotWebAppUrl: null,
    peerDisplayName: "Alex Doe",
    peerIsBot: false,
    peerLastSeenAt: null,
    peerOnline: false,
    peerPhoneNumber: "+375291234567",
    peerUserId: "user-2",
    pinOrder: null,
    photoUrl: null,
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

describe("chatsListPresentation", () => {
  it("builds feature flags with defaults and overrides", () => {
    expect(buildChatsFeatureFlags({ calls: false, stories: true })).toEqual({
      bots: false,
      calls: false,
      stories: true
    });
  });

  it("builds filter options with bots toggle", () => {
    expect(buildChatFilterOptions(buildChatsFeatureFlags({ bots: false }))).toHaveLength(5);
    expect(buildChatFilterOptions(buildChatsFeatureFlags({ bots: true }))).toHaveLength(6);
  });

  it("formats auto delete labels", () => {
    expect(formatAutoDelete(20)).toBe("TTL 20s");
    expect(formatAutoDelete(120)).toBe("TTL 2m");
    expect(formatAutoDelete(7200)).toBe("TTL 2h");
  });

  it("matches chats by filter and search", () => {
    const unreadChat = createChat({ unreadCount: 3 });
    const botChat = createChat({ peerIsBot: true });

    expect(matchesFilter(unreadChat, "UNREAD")).toBe(true);
    expect(matchesFilter(botChat, "BOTS")).toBe(true);
    expect(matchesSearch(createChat({ draftText: "follow up later" }), "follow up")).toBe(true);
  });

  it("formats direct and group metadata", () => {
    expect(formatChatMeta(createChat({ peerIsBot: true, peerBotSupportsInline: true }))).toContain(
      "bot"
    );
    expect(
      formatChatMeta(
        createChat({
          chatType: "GROUP",
          forumEnabled: true,
          memberCount: 42,
          publicUsername: "team",
          title: "Team",
          topicCount: 3
        })
      )
    ).toBe("@team - 42 members - 3 topics");
    expect(
      formatChatMeta(
        createChat({
          chatType: "CHANNEL",
          memberCount: 1200,
          publicUsername: "news",
          title: "News"
        })
      )
    ).toBe("@news - 1200 subscribers");
  });

  it("summarizes unread messages across chats", () => {
    expect(summarizeUnread([createChat({ unreadCount: 2 }), createChat({ unreadCount: 5 })])).toBe(
      7
    );
  });
});
