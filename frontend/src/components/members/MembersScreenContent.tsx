import React from "react";
import { MembersHeaderContent } from "./MembersHeaderContent";
import { MembersList } from "./MembersList";
import type { MembersScreenController } from "./useMembersScreenController";
import { AppHeader } from "../ui/AppHeader";
import { ScreenFeedback } from "../ui/ScreenFeedback";
import { appSpacing } from "../../theme/tokens";
import type { ChatSummary } from "../../types";

type MembersScreenContentProps = {
  chat: ChatSummary;
  controller: MembersScreenController;
  currentUserId: string;
  onClose: () => void;
  onOpenDiscussionChat?: (chatId: string) => void;
  onOpenSharedMedia?: (chat: ChatSummary) => void;
};

export function MembersScreenContent({
  chat,
  controller,
  currentUserId,
  onClose,
  onOpenDiscussionChat,
  onOpenSharedMedia
}: MembersScreenContentProps) {
  const headerContent = (
    <MembersHeaderContent
      analytics={controller.analytics}
      autoDeleteSeconds={controller.autoDeleteSeconds}
      availableDiscussionChats={controller.availableDiscussionChats}
      bannedMembers={controller.bannedMembers}
      canApproveJoinRequests={controller.canApproveJoinRequests}
      canLeaveChat={controller.canLeaveChat}
      canManageInviteLinks={controller.canManageInviteLinks}
      canManageMembers={controller.canManageMembers}
      canModerateMessages={controller.canModerateMessages}
      canViewAnalytics={controller.canViewAnalytics}
      chat={chat}
      chatAbout={controller.chatAbout}
      chatPhotoUrl={controller.chatPhotoUrl}
      chatTitle={controller.chatTitle}
      commentsEnabled={controller.commentsEnabled}
      creatingInviteLink={controller.creatingInviteLink}
      crossPostingEnabled={controller.crossPostingEnabled}
      discussionChatId={controller.discussionChatId}
      forumEnabled={controller.forumEnabled}
      inviteLabel={controller.inviteLabel}
      inviteLinks={controller.inviteLinks}
      inviteUsageLimit={controller.inviteUsageLimit}
      joinRequests={controller.joinRequests}
      joinRequiresApproval={controller.joinRequiresApproval}
      loadingAnalytics={controller.loadingAnalytics}
      loadingBans={controller.loadingBans}
      loadingInviteLinks={controller.loadingInviteLinks}
      loadingJoinRequests={controller.loadingJoinRequests}
      mutating={controller.mutating}
      onAboutChange={controller.setChatAbout}
      onAddMembers={controller.handleAddMembers}
      onApproveJoinRequest={(userId) => void controller.handleApproveJoinRequest(userId)}
      onArchiveToggle={() => void controller.handleArchiveToggle()}
      onAutoDeleteSecondsChange={controller.setAutoDeleteSeconds}
      onClearHistory={() => void controller.handleClearHistory()}
      onCommentsEnabledChange={controller.setCommentsEnabled}
      onCreateInviteLink={() => void controller.handleCreateInviteLink()}
      onCrossPostingEnabledChange={controller.setCrossPostingEnabled}
      onDeclineJoinRequest={(userId) => void controller.handleDeclineJoinRequest(userId)}
      onDiscussionChatChange={controller.setDiscussionChatId}
      onForumEnabledChange={controller.setForumEnabled}
      onInviteLabelChange={controller.setInviteLabel}
      onInviteUsageLimitChange={controller.setInviteUsageLimit}
      onLeaveChat={() => void controller.handleLeaveChat()}
      onMarkUnread={() => void controller.handleMarkUnread()}
      onMuteToggle={() => void controller.handleMuteToggle()}
      onOpenDiscussionChat={onOpenDiscussionChat}
      onOpenSharedMedia={onOpenSharedMedia}
      onPinToggle={() => void controller.handlePinToggle()}
      onPublicUsernameChange={controller.setPublicUsername}
      onQueryChange={controller.setQuery}
      onReactionsEnabledChange={controller.setReactionsEnabled}
      onRemovePhoto={() => void controller.handleRemovePhoto()}
      onReportChat={() => void controller.handleReportChat()}
      onRestrictedJoinRequiresApprovalChange={controller.setJoinRequiresApproval}
      onRevokeInviteLink={(inviteLinkId) => void controller.handleRevokeInviteLink(inviteLinkId)}
      onSaveProfile={() => void controller.handleSaveProfile()}
      onSavePublicUsername={() => void controller.handleSavePublicUsername()}
      onSearchCandidateToggle={controller.toggleCandidate}
      onSlowModeSecondsChange={controller.setSlowModeSeconds}
      onTitleChange={controller.setChatTitle}
      onUnbanMember={(userId) => void controller.handleUnbanMember(userId)}
      onUploadPhoto={() => void controller.handleUploadPhoto()}
      processingJoinRequestUserId={controller.processingJoinRequestUserId}
      publicUsername={controller.publicUsername}
      query={controller.query}
      reactionsEnabled={controller.reactionsEnabled}
      removingPhoto={controller.removingPhoto}
      resolvedMuted={controller.resolvedMuted}
      restrictedMembersCount={controller.restrictedMembers.length}
      results={controller.results}
      revokingInviteLinkId={controller.revokingInviteLinkId}
      savingProfile={controller.savingProfile}
      savingPublicUsername={controller.savingPublicUsername}
      searching={controller.searching}
      selectedUserIds={controller.selectedUserIds}
      slowModeSeconds={controller.slowModeSeconds}
      unbanningUserId={controller.unbanningUserId}
      updatingChatAction={controller.updatingChatAction}
      uploadingPhoto={controller.uploadingPhoto}
    />
  );

  return (
    <>
      <AppHeader onBack={onClose} subtitle={controller.chatTitle} title="Members" />
      <ScreenFeedback
        error={controller.error}
        loading={controller.loadingMembers}
        loadingStyle={{ marginVertical: appSpacing.md }}
        notice={controller.notice}
      />

      <MembersList
        banningUserId={controller.banningUserId}
        canManageMembers={controller.canManageMembers}
        canModerateMessages={controller.canModerateMessages}
        chatType={chat.chatType}
        currentUserId={currentUserId}
        headerContent={headerContent}
        memberPresenceByUserId={controller.memberPresenceByUserId}
        members={controller.orderedMembers}
        onBanMember={(userId) => void controller.handleBanMember(userId)}
        onPermissionChange={(userId, payload) =>
          void controller.handlePermissionChange(userId, payload)
        }
        onRemoveMember={(userId) => void controller.handleRemoveMember(userId)}
        onRestrictionChange={(userId, nextCanSendMessages, restrictedUntil, restrictionReason) =>
          void controller.handleRestrictionChange(
            userId,
            nextCanSendMessages,
            restrictedUntil,
            restrictionReason
          )
        }
        onRoleChange={(userId, role) => void controller.handleRoleChange(userId, role)}
        restrictingUserId={controller.restrictingUserId}
        updatingPermissionsUserId={controller.updatingPermissionsUserId}
      />
    </>
  );
}
