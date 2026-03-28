import type {
  ChatMessage,
  ChatSummary,
  GlobalMessageSearchResult,
  GlobalSearchResponse,
  UserSearchResult
} from "../../types";
import {
  buildGlobalSearchLinkAction,
  buildGlobalSearchInfoText,
  buildGlobalSearchSummary,
  buildGlobalSearchUserMeta,
  describeGlobalSearchChat,
  describeGlobalSearchMessage,
  findExactPublicChatMatch,
  hasGlobalSearchResults
} from "./globalSearchPresentation";

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

function createMessage(overrides: Partial<ChatMessage> = {}): ChatMessage {
  return {
    chatId: "chat-1",
    messageId: "message-1",
    clientMessageId: null,
    senderId: "user-1",
    displaySenderName: "Alex",
    displaySenderPhotoUrl: null,
    displaySenderPhotoAccessExpiresAt: null,
    anonymousSender: false,
    recipientId: null,
    viaBotUserId: null,
    topicId: null,
    threadRootMessageId: null,
    discussionChatId: null,
    discussionRootMessageId: null,
    commentCount: 0,
    text: "hello world",
    entities: [],
    messageType: "TEXT",
    caption: null,
    silent: false,
    location: null,
    contactCard: null,
    serviceMessage: null,
    createdAt: "2026-03-27T12:00:00.000Z",
    replyToMessageId: null,
    forwardedFromChatId: null,
    forwardedFromMessageId: null,
    poll: null,
    sticker: null,
    attachments: [],
    reactions: [],
    deliveryStatus: "SENT",
    deliveredAt: null,
    readAt: null,
    expiresAt: null,
    editedAt: null,
    deletedAt: null,
    ...overrides
  };
}

describe("globalSearchPresentation", () => {
  it("builds summary, info text, and detects empty states", () => {
    const results: GlobalSearchResponse = {
      query: "al",
      users: [createUser()],
      chats: [createChat()],
      messages: []
    };

    expect(hasGlobalSearchResults(results)).toBe(true);
    expect(buildGlobalSearchSummary(results)).toBe("2 results");
    expect(buildGlobalSearchInfoText("al", false, "2 results")).toBe("2 results");
    expect(buildGlobalSearchInfoText("a", false, null)).toContain("Type at least 2 characters");
    expect(buildGlobalSearchInfoText("alex", true, null)).toBe("Searching...");
    expect(
      buildGlobalSearchInfoText(
        "https://t.me/+invite-token",
        false,
        null,
        buildGlobalSearchLinkAction({ type: "JOIN", token: "invite-token" })
      )
    ).toContain("Recognized a link");
  });

  it("describes users, chats, and message results", () => {
    const directChat = createChat({
      chatType: "DIRECT",
      peerDisplayName: "Alex",
      peerPhoneNumber: "+375291111111",
      peerOnline: true
    });
    const messageResult: GlobalMessageSearchResult = {
      chat: createChat(),
      message: createMessage({
        poll: {
          pollId: "poll-1",
          question: "Lunch?",
          multipleChoice: false,
          options: [],
          closed: false,
          totalVoters: 0
        }
      })
    };

    expect(buildGlobalSearchUserMeta(createUser())).toEqual(
      expect.arrayContaining(["@alex", "online - +375291111111"])
    );
    expect(describeGlobalSearchChat(directChat)).toContain("online");
    expect(describeGlobalSearchChat(createChat({ chatType: "SAVED" }))).toBe("Private notes");
    expect(
      describeGlobalSearchChat(
        createChat({
          chatType: "CHANNEL",
          memberCount: 1200,
          publicUsername: "news",
          title: "News"
        })
      )
    ).toBe("@news - 1200 subscribers");
    expect(describeGlobalSearchMessage(messageResult)).toBe("Poll: Lunch?");
  });

  it("builds quick actions for parsed links", () => {
    expect(buildGlobalSearchLinkAction({ type: "JOIN", token: "@team" })).toEqual(
      expect.objectContaining({
        ctaLabel: "Open join flow",
        title: "Open chat link"
      })
    );
    expect(
      buildGlobalSearchLinkAction(
        { type: "JOIN", token: "@team" },
        createChat({
          chatId: "chat-team",
          chatType: "GROUP",
          publicUsername: "team",
          title: "Team"
        })
      )
    ).toEqual(
      expect.objectContaining({
        ctaLabel: "Open chat",
        title: "Open linked chat"
      })
    );
    expect(buildGlobalSearchLinkAction({ type: "CALL", token: "room-1" })).toEqual(
      expect.objectContaining({
        ctaLabel: "Join call",
        title: "Open call link"
      })
    );
    expect(
      buildGlobalSearchLinkAction({
        type: "CHAT",
        chatId: "chat-1",
        topicId: "topic-9"
      })
    ).toEqual(
      expect.objectContaining({
        ctaLabel: "Open chat",
        title: "Open linked chat"
      })
    );
    expect(
      findExactPublicChatMatch(
        [
          createChat({
            chatId: "chat-team",
            chatType: "GROUP",
            publicUsername: "Team",
            title: "Team"
          })
        ],
        {
          type: "JOIN",
          token: "@team"
        }
      )
    ).toEqual(
      expect.objectContaining({
        chatId: "chat-team"
      })
    );
  });
});
