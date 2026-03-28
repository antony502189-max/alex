import type { ChatSummary } from "../types";
import { getChatInfoPresentation } from "./chatInfoPresentation";

function createChat(overrides: Partial<ChatSummary> = {}): ChatSummary {
  return {
    about: "Base description",
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
    memberCount: 12,
    mentionCount: 0,
    mutedUntil: null,
    peerBotSupportsInline: false,
    peerBotWebAppUrl: null,
    peerDisplayName: "Alice",
    peerIsBot: false,
    peerLastSeenAt: null,
    peerOnline: true,
    peerPhoneNumber: "+375291112233",
    peerUserId: "user-2",
    photoAccessExpiresAt: null,
    photoUrl: null,
    pinOrder: null,
    pinned: false,
    pinnedMessageId: null,
    publicUsername: "alice",
    reactionsEnabled: true,
    replyCount: 0,
    slowModeSeconds: null,
    title: "Alice",
    topicCount: 0,
    unreadCount: 0,
    ...overrides
  };
}

describe("getChatInfoPresentation", () => {
  it("returns direct-chat presentation with moderation actions", () => {
    const presentation = getChatInfoPresentation(createChat());

    expect(presentation.screenSubtitle).toBe("Direct chat profile and controls");
    expect(presentation.heroTitle).toBe("Alice");
    expect(presentation.heroSubtitle).toBe("online");
    expect(presentation.heroMeta).toEqual(["@alice", "+375291112233"]);
    expect(presentation.showBlockUserAction).toBe(true);
    expect(presentation.showReportUserAction).toBe(true);
    expect(presentation.showOpenMiniAppAction).toBe(false);
    expect(presentation.showOpenMembersAction).toBe(false);
  });

  it("returns bot-specific direct-chat presentation without human presence wording", () => {
    const presentation = getChatInfoPresentation(
      createChat({
        about: null,
        peerBotSupportsInline: true,
        peerBotWebAppUrl: "https://example.com/bot-app",
        peerDisplayName: "Gif Bot",
        peerIsBot: true,
        peerOnline: false,
        peerPhoneNumber: null,
        title: "Gif Bot"
      })
    );

    expect(presentation.screenSubtitle).toBe("Bot profile and controls");
    expect(presentation.heroSubtitle).toBe("bot account - inline enabled - mini app available");
    expect(presentation.profileTitle).toBe("Bot profile");
    expect(presentation.profileDescription).toBe("This bot has not added a public description yet.");
    expect(presentation.detailItems).toEqual(
      expect.arrayContaining(["Inline mode: enabled", "Mini app: available"])
    );
    expect(presentation.showOpenMiniAppAction).toBe(true);
  });

  it("returns group presentation with manage and moderation affordances", () => {
    const presentation = getChatInfoPresentation(
      createChat({
        chatType: "GROUP",
        memberCount: 48,
        peerDisplayName: null,
        peerPhoneNumber: null,
        peerUserId: null,
        publicUsername: "team",
        title: "Team",
        forumEnabled: true,
        joinRequiresApproval: true,
        autoDeleteSeconds: 3600,
        slowModeSeconds: 30
      })
    );

    expect(presentation.screenSubtitle).toBe("Group profile and controls");
    expect(presentation.heroSubtitle).toBe("48 members");
    expect(presentation.manageActionTitle).toBe("Manage group");
    expect(presentation.showOpenMembersAction).toBe(true);
    expect(presentation.showLeaveChatAction).toBe(true);
    expect(presentation.showReportChatAction).toBe(true);
    expect(presentation.showBlockUserAction).toBe(false);
    expect(presentation.detailItems).toEqual(
      expect.arrayContaining([
        "Members: 48 members",
        "Public username: @team",
        "Joining: public requests require approval",
        "Topics: enabled",
        "Auto-delete: 1 hour",
        "Slow mode: 30 seconds"
      ])
    );
  });

  it("returns channel presentation with discussion and audience details", () => {
    const presentation = getChatInfoPresentation(
      createChat({
        chatType: "CHANNEL",
        about: null,
        commentsEnabled: true,
        crossPostingEnabled: true,
        linkedDiscussionChatTitle: "Channel discussion",
        memberCount: 1200,
        peerDisplayName: null,
        peerPhoneNumber: null,
        peerUserId: null,
        publicUsername: null,
        title: "News"
      })
    );

    expect(presentation.screenSubtitle).toBe("Channel profile and controls");
    expect(presentation.heroSubtitle).toBe("1200 subscribers");
    expect(presentation.profileLabel).toBe("Private channel");
    expect(presentation.manageActionTitle).toBe("Manage channel");
    expect(presentation.detailItems).toEqual(
      expect.arrayContaining([
        "Audience: 1200 subscribers",
        "Visibility: invite-only",
        "Joining: invite only",
        "Comments: enabled",
        "Cross-posting: enabled",
        "Discussion chat: Channel discussion"
      ])
    );
  });

  it("distinguishes private approval-only chat access from open public join", () => {
    const privateApprovalGroup = getChatInfoPresentation(
      createChat({
        chatType: "GROUP",
        peerDisplayName: null,
        peerPhoneNumber: null,
        peerUserId: null,
        publicUsername: null,
        joinRequiresApproval: true,
        title: "Private Team"
      })
    );
    const publicOpenGroup = getChatInfoPresentation(
      createChat({
        chatType: "GROUP",
        peerDisplayName: null,
        peerPhoneNumber: null,
        peerUserId: null,
        publicUsername: "open-team",
        joinRequiresApproval: false,
        title: "Open Team"
      })
    );

    expect(privateApprovalGroup.detailItems).toEqual(
      expect.arrayContaining([
        "Visibility: invite-only",
        "Joining: invite link requests require approval"
      ])
    );
    expect(publicOpenGroup.detailItems).toEqual(
      expect.arrayContaining([
        "Public username: @open-team",
        "Joining: public join enabled"
      ])
    );
  });
});
