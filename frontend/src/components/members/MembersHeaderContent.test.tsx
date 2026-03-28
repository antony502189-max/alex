import React from "react";
import { fireEvent, render } from "@testing-library/react-native";
import { Share } from "react-native";
import { MembersHeaderContent } from "./MembersHeaderContent";
import type { ChatInviteLink, ChatSummary } from "../../types";

function createChat(overrides: Partial<ChatSummary> = {}): ChatSummary {
  return {
    about: null,
    archived: false,
    autoDeleteSeconds: null,
    chatId: "chat-1",
    chatType: "GROUP",
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

function createInviteLink(overrides: Partial<ChatInviteLink> = {}): ChatInviteLink {
  return {
    createdAt: "2026-03-28T10:00:00.000Z",
    chatId: "chat-1",
    expiresAt: null,
    inviteLinkId: "invite-1",
    label: "Main link",
    lastUsedAt: null,
    revoked: false,
    shareUrl: "https://alex.example/invite-token",
    token: "invite-token",
    usageCount: 3,
    usageLimit: null,
    ...overrides
  };
}

function createProps() {
  return {
    analytics: null,
    autoDeleteSeconds: "",
    availableDiscussionChats: [],
    bannedMembers: [],
    canApproveJoinRequests: false,
    canLeaveChat: true,
    canManageInviteLinks: true,
    canManageMembers: false,
    canModerateMessages: false,
    canViewAnalytics: false,
    chat: createChat(),
    chatAbout: "",
    chatPhotoUrl: null,
    chatTitle: "Team",
    commentsEnabled: false,
    creatingInviteLink: false,
    crossPostingEnabled: false,
    discussionChatId: null,
    forumEnabled: false,
    inviteLabel: "",
    inviteLinks: [createInviteLink()],
    inviteUsageLimit: "",
    joinRequests: [],
    joinRequiresApproval: false,
    loadingAnalytics: false,
    loadingBans: false,
    loadingInviteLinks: false,
    loadingJoinRequests: false,
    mutating: false,
    onAboutChange: jest.fn(),
    onAddMembers: jest.fn(),
    onApproveJoinRequest: jest.fn(),
    onArchiveToggle: jest.fn(),
    onAutoDeleteSecondsChange: jest.fn(),
    onClearHistory: jest.fn(),
    onCommentsEnabledChange: jest.fn(),
    onCreateInviteLink: jest.fn(),
    onCrossPostingEnabledChange: jest.fn(),
    onDeclineJoinRequest: jest.fn(),
    onDiscussionChatChange: jest.fn(),
    onForumEnabledChange: jest.fn(),
    onInviteLabelChange: jest.fn(),
    onInviteUsageLimitChange: jest.fn(),
    onLeaveChat: jest.fn(),
    onMarkUnread: jest.fn(),
    onMuteToggle: jest.fn(),
    onOpenSharedMedia: jest.fn(),
    onPinToggle: jest.fn(),
    onPublicUsernameChange: jest.fn(),
    onQueryChange: jest.fn(),
    onReactionsEnabledChange: jest.fn(),
    onRemovePhoto: jest.fn(),
    onReportChat: jest.fn(),
    onRestrictedJoinRequiresApprovalChange: jest.fn(),
    onRevokeInviteLink: jest.fn(),
    onSaveProfile: jest.fn(),
    onSavePublicUsername: jest.fn(),
    onSearchCandidateToggle: jest.fn(),
    onSlowModeSecondsChange: jest.fn(),
    onTitleChange: jest.fn(),
    onUnbanMember: jest.fn(),
    onUploadPhoto: jest.fn(),
    processingJoinRequestUserId: null,
    publicUsername: "team",
    query: "",
    reactionsEnabled: true,
    removingPhoto: false,
    resolvedMuted: false,
    restrictedMembersCount: 0,
    results: [],
    revokingInviteLinkId: null,
    savingProfile: false,
    savingPublicUsername: false,
    searching: false,
    selectedUserIds: [],
    slowModeSeconds: "",
    unbanningUserId: null,
    updatingChatAction: null,
    uploadingPhoto: false
  };
}

describe("MembersHeaderContent", () => {
  it("shares the saved public username link and active invite links", () => {
    const shareSpy = jest.spyOn(Share, "share").mockResolvedValue({
      action: "sharedAction"
    });

    const screen = render(<MembersHeaderContent {...createProps()} />);

    fireEvent.press(screen.getByText("Share public link"));
    fireEvent.press(screen.getByText("Share link"));

    expect(shareSpy).toHaveBeenNthCalledWith(1, {
      message: "https://alex.example/join/team",
      url: "https://alex.example/join/team"
    });
    expect(shareSpy).toHaveBeenNthCalledWith(2, {
      message: "https://alex.example/invite-token",
      url: "https://alex.example/invite-token"
    });

    shareSpy.mockRestore();
  });

  it("opens the linked discussion group from chat actions when a channel has one", () => {
    const onOpenDiscussionChat = jest.fn();
    const screen = render(
      <MembersHeaderContent
        {...createProps()}
        chat={createChat({
          chatType: "CHANNEL",
          linkedDiscussionChatId: "chat-discussion",
          linkedDiscussionChatTitle: "Team Discussion"
        })}
        onOpenDiscussionChat={onOpenDiscussionChat}
      />
    );

    fireEvent.press(screen.getByText("Open discussion group"));

    expect(onOpenDiscussionChat).toHaveBeenCalledWith("chat-discussion");
  });

  it("shows expired invite links without a share action while keeping revoke available", () => {
    const onRevokeInviteLink = jest.fn();
    const screen = render(
      <MembersHeaderContent
        {...createProps()}
        inviteLinks={[createInviteLink({ expiresAt: "2000-03-01T10:00:00.000Z" })]}
        onRevokeInviteLink={onRevokeInviteLink}
      />
    );

    expect(screen.getByText("Expired")).toBeTruthy();
    expect(screen.queryByText("Share link")).toBeNull();

    fireEvent.press(screen.getByText("Revoke"));

    expect(onRevokeInviteLink).toHaveBeenCalledWith("invite-1");
  });

  it("shows limit-reached invite links without a share action while keeping revoke available", () => {
    const onRevokeInviteLink = jest.fn();
    const screen = render(
      <MembersHeaderContent
        {...createProps()}
        inviteLinks={[createInviteLink({ usageCount: 3, usageLimit: 3 })]}
        onRevokeInviteLink={onRevokeInviteLink}
      />
    );

    expect(screen.getByText("Limit reached")).toBeTruthy();
    expect(screen.queryByText("Share link")).toBeNull();

    fireEvent.press(screen.getByText("Revoke"));

    expect(onRevokeInviteLink).toHaveBeenCalledWith("invite-1");
  });
});
