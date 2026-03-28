import React from "react";
import {
  ActivityIndicator,
  FlatList,
  Pressable,
  Share,
  StyleSheet,
  Text,
  View
} from "react-native";
import { Avatar } from "../Avatar";
import { AppActionTile } from "../ui/AppActionTile";
import { AppButton } from "../ui/AppButton";
import { AppTextField } from "../ui/AppTextField";
import { AppToggleCard } from "../ui/AppToggleCard";
import { SectionCard } from "../ui/SectionCard";
import { appColors, appRadii, appSpacing } from "../../theme/tokens";
import type {
  ChatAnalytics,
  ChatBan,
  ChatInviteLink,
  ChatJoinRequest,
  ChatSummary,
  UserSearchResult
} from "../../types";
import {
  buildPublicChatShareUrl,
  getMembersChatTypeLabel,
  isInviteLinkExpired,
  isInviteLinkLimitReached
} from "./membersPresentation";

type MembersHeaderContentProps = {
  analytics: ChatAnalytics | null;
  autoDeleteSeconds: string;
  availableDiscussionChats: ChatSummary[];
  bannedMembers: ChatBan[];
  canApproveJoinRequests: boolean;
  canLeaveChat: boolean;
  canManageInviteLinks: boolean;
  canManageMembers: boolean;
  canModerateMessages: boolean;
  canViewAnalytics: boolean;
  chat: ChatSummary;
  chatAbout: string;
  chatPhotoUrl: string | null;
  chatTitle: string;
  commentsEnabled: boolean;
  creatingInviteLink: boolean;
  crossPostingEnabled: boolean;
  discussionChatId: string | null;
  forumEnabled: boolean;
  inviteLabel: string;
  inviteLinks: ChatInviteLink[];
  inviteUsageLimit: string;
  joinRequests: ChatJoinRequest[];
  joinRequiresApproval: boolean;
  loadingAnalytics: boolean;
  loadingBans: boolean;
  loadingInviteLinks: boolean;
  loadingJoinRequests: boolean;
  mutating: boolean;
  onAddMembers: () => void;
  onApproveJoinRequest: (userId: string) => void;
  onArchiveToggle: () => void;
  onClearHistory: () => void;
  onCreateInviteLink: () => void;
  onDeclineJoinRequest: (userId: string) => void;
  onInviteLabelChange: (value: string) => void;
  onInviteUsageLimitChange: (value: string) => void;
  onLeaveChat: () => void;
  onMarkUnread: () => void;
  onMuteToggle: () => void;
  onOpenDiscussionChat?: (chatId: string) => void;
  onOpenSharedMedia?: (chat: ChatSummary) => void;
  onPinToggle: () => void;
  onPublicUsernameChange: (value: string) => void;
  onQueryChange: (value: string) => void;
  onReactionsEnabledChange: (value: boolean) => void;
  onRemovePhoto: () => void;
  onReportChat: () => void;
  onRestrictedJoinRequiresApprovalChange: (value: boolean) => void;
  onRevokeInviteLink: (inviteLinkId: string) => void;
  onSaveProfile: () => void;
  onSavePublicUsername: () => void;
  onSearchCandidateToggle: (userId: string) => void;
  onSlowModeSecondsChange: (value: string) => void;
  onTitleChange: (value: string) => void;
  onUnbanMember: (userId: string) => void;
  onUploadPhoto: () => void;
  onAboutChange: (value: string) => void;
  onAutoDeleteSecondsChange: (value: string) => void;
  onCommentsEnabledChange: (value: boolean) => void;
  onCrossPostingEnabledChange: (value: boolean) => void;
  onDiscussionChatChange: (chatId: string) => void;
  onForumEnabledChange: (value: boolean) => void;
  processingJoinRequestUserId: string | null;
  publicUsername: string;
  query: string;
  reactionsEnabled: boolean;
  removingPhoto: boolean;
  resolvedMuted: boolean;
  restrictedMembersCount: number;
  results: UserSearchResult[];
  revokingInviteLinkId: string | null;
  savingProfile: boolean;
  savingPublicUsername: boolean;
  searching: boolean;
  selectedUserIds: string[];
  slowModeSeconds: string;
  unbanningUserId: string | null;
  updatingChatAction: string | null;
  uploadingPhoto: boolean;
};

export function MembersHeaderContent({
  analytics,
  autoDeleteSeconds,
  availableDiscussionChats,
  bannedMembers,
  canApproveJoinRequests,
  canLeaveChat,
  canManageInviteLinks,
  canManageMembers,
  canModerateMessages,
  canViewAnalytics,
  chat,
  chatAbout,
  chatPhotoUrl,
  chatTitle,
  commentsEnabled,
  creatingInviteLink,
  crossPostingEnabled,
  discussionChatId,
  forumEnabled,
  inviteLabel,
  inviteLinks,
  inviteUsageLimit,
  joinRequests,
  joinRequiresApproval,
  loadingAnalytics,
  loadingBans,
  loadingInviteLinks,
  loadingJoinRequests,
  mutating,
  onAboutChange,
  onAddMembers,
  onApproveJoinRequest,
  onArchiveToggle,
  onAutoDeleteSecondsChange,
  onClearHistory,
  onCommentsEnabledChange,
  onCreateInviteLink,
  onCrossPostingEnabledChange,
  onDeclineJoinRequest,
  onDiscussionChatChange,
  onForumEnabledChange,
  onInviteLabelChange,
  onInviteUsageLimitChange,
  onLeaveChat,
  onMarkUnread,
  onMuteToggle,
  onOpenDiscussionChat,
  onOpenSharedMedia,
  onPinToggle,
  onPublicUsernameChange,
  onQueryChange,
  onReactionsEnabledChange,
  onRemovePhoto,
  onReportChat,
  onRestrictedJoinRequiresApprovalChange,
  onRevokeInviteLink,
  onSaveProfile,
  onSavePublicUsername,
  onSearchCandidateToggle,
  onSlowModeSecondsChange,
  onTitleChange,
  onUnbanMember,
  onUploadPhoto,
  processingJoinRequestUserId,
  publicUsername,
  query,
  reactionsEnabled,
  removingPhoto,
  resolvedMuted,
  restrictedMembersCount,
  results,
  revokingInviteLinkId,
  savingProfile,
  savingPublicUsername,
  searching,
  selectedUserIds,
  slowModeSeconds,
  unbanningUserId,
  updatingChatAction,
  uploadingPhoto
}: MembersHeaderContentProps) {
  const publicChatShareUrl = buildPublicChatShareUrl(chat.publicUsername);

  function handleShareUrl(url: string) {
    void Share.share({
      message: url,
      url
    }).catch(() => undefined);
  }

  return (
    <View style={styles.headerContent}>
      <SectionCard style={styles.chatIdentityCard}>
        <Avatar uri={chatPhotoUrl} title={chatTitle} size={92} />
        <View style={styles.chatIdentityText}>
          <Text style={styles.chatIdentityTitle}>{chatTitle}</Text>
          <Text style={styles.chatIdentityMeta}>{getMembersChatTypeLabel(chat.chatType)}</Text>
        </View>
        {canManageInviteLinks ? (
          <View style={styles.photoActions}>
            <AppButton
              disabled={uploadingPhoto}
              fullWidth
              onPress={onUploadPhoto}
              variant="secondary"
            >
              {uploadingPhoto ? "Uploading..." : "Change photo"}
            </AppButton>
            <AppButton
              disabled={removingPhoto || !chatPhotoUrl}
              fullWidth
              onPress={onRemovePhoto}
              variant="danger"
            >
              {removingPhoto ? "Removing..." : "Remove photo"}
            </AppButton>
          </View>
        ) : null}
      </SectionCard>

      <SectionCard
        description="Quick management tools for the current conversation."
        title="Chat actions"
      >
        <View style={styles.chatActionGrid}>
          <AppActionTile
            body="Browse shared files, links, photos, and videos."
            disabled={updatingChatAction !== null}
            onPress={() => onOpenSharedMedia?.(chat)}
            title="Shared media"
          />
          {chat.chatType === "CHANNEL" && chat.linkedDiscussionChatId && onOpenDiscussionChat ? (
            <AppActionTile
              body={
                chat.linkedDiscussionChatTitle
                  ? `Open ${chat.linkedDiscussionChatTitle} for comments and discussion threads.`
                  : "Open the linked discussion group for comments and discussion threads."
              }
              disabled={updatingChatAction !== null}
              onPress={() => onOpenDiscussionChat(chat.linkedDiscussionChatId!)}
              title="Open discussion group"
            />
          ) : null}
          <AppActionTile
            body="Move chat in or out of archive."
            disabled={updatingChatAction !== null}
            onPress={onArchiveToggle}
            title={chat.archived ? "Unarchive" : "Archive"}
          />
          <AppActionTile
            body="Control notifications for this chat."
            disabled={updatingChatAction !== null}
            onPress={onMuteToggle}
            title={resolvedMuted ? "Unmute" : "Mute 24h"}
          />
          <AppActionTile
            body="Bring the chat back to the unread list."
            disabled={updatingChatAction !== null}
            onPress={onMarkUnread}
            title="Mark unread"
          />
          <AppActionTile
            body="Keep the chat near the top of the list."
            disabled={updatingChatAction !== null}
            onPress={onPinToggle}
            title={chat.pinned ? "Unpin" : "Pin to top"}
          />
          <AppActionTile
            body="Remove current chat history on this account."
            disabled={updatingChatAction !== null}
            onPress={onClearHistory}
            title="Clear history"
          />
          <AppActionTile
            body="Send an abuse report for this chat."
            disabled={updatingChatAction !== null}
            onPress={onReportChat}
            title="Report"
          />
          {canLeaveChat ? (
            <AppActionTile
              body="Exit this conversation on the current account."
              disabled={updatingChatAction !== null}
              onPress={onLeaveChat}
              title="Leave chat"
              tone="danger"
            />
          ) : null}
        </View>
      </SectionCard>

      {canViewAnalytics ? (
        <SectionCard
          description="Basic public-surface metrics for the last 24 hours."
          title="Analytics"
        >
          {loadingAnalytics ? <ActivityIndicator color={appColors.textPrimary} style={styles.loader} /> : null}
          {analytics ? (
            <View style={styles.analyticsGrid}>
              <AnalyticsTile
                label={chat.chatType === "CHANNEL" ? "Subscribers" : "Members"}
                value={analytics.memberCount}
              />
              <AnalyticsTile label="Admins" value={analytics.adminCount} />
              <AnalyticsTile label="Pending joins" value={analytics.pendingJoinRequestCount} />
              <AnalyticsTile label="Active links" value={analytics.activeInviteLinkCount} />
              <AnalyticsTile label="Posts 24h" value={analytics.messagesLast24h} />
              <AnalyticsTile label="Reactions 24h" value={analytics.reactionsLast24h} />
              {chat.chatType === "CHANNEL" ? (
                <AnalyticsTile label="Comments 24h" value={analytics.commentsLast24h} />
              ) : null}
              <AnalyticsTile label="Restricted" value={analytics.restrictedCount} />
              <AnalyticsTile label="Banned" value={analytics.bannedCount} />
            </View>
          ) : null}
        </SectionCard>
      ) : null}

      {canManageMembers ? (
        <SectionCard title="Add people">
          <AppTextField
            autoCapitalize="none"
            onChangeText={onQueryChange}
            placeholder="Search users"
            style={styles.input}
            value={query}
          />

          {searching ? <ActivityIndicator color={appColors.textPrimary} style={styles.loader} /> : null}

          {results.length > 0 ? (
            <FlatList
              contentContainerStyle={styles.candidatesContent}
              data={results}
              horizontal
              keyExtractor={(item) => item.userId}
              renderItem={({ item }) => {
                const selected = selectedUserIds.includes(item.userId);
                return (
                  <Pressable
                    onPress={() => onSearchCandidateToggle(item.userId)}
                    style={[styles.candidateCard, selected && styles.candidateCardSelected]}
                  >
                    <Avatar uri={item.photoUrl} title={item.displayName} size={46} />
                    <Text style={styles.candidateName}>{item.displayName}</Text>
                    <Text style={styles.candidateMeta}>{item.phoneNumber}</Text>
                  </Pressable>
                );
              }}
              showsHorizontalScrollIndicator={false}
            />
          ) : null}

          <AppButton
            disabled={mutating || selectedUserIds.length === 0}
            fullWidth
            onPress={onAddMembers}
            variant="primary"
          >
            {mutating ? "Updating..." : `Add selected (${selectedUserIds.length})`}
          </AppButton>
        </SectionCard>
      ) : null}

      {canManageInviteLinks ? (
        <SectionCard title="Chat profile">
          <AppTextField
            onChangeText={onTitleChange}
            placeholder="Chat title"
            style={styles.input}
            value={chatTitle}
          />
          <AppTextField
            multiline
            onChangeText={onAboutChange}
            placeholder="Description"
            style={[styles.input, styles.aboutInput]}
            value={chatAbout}
          />
          <AppTextField
            keyboardType="number-pad"
            onChangeText={onAutoDeleteSecondsChange}
            placeholder="Auto-delete seconds"
            style={styles.input}
            value={autoDeleteSeconds}
          />
          <AppTextField
            keyboardType="number-pad"
            onChangeText={onSlowModeSecondsChange}
            placeholder="Slow mode seconds"
            style={styles.input}
            value={slowModeSeconds}
          />
          {chat.chatType === "GROUP" ? (
            <AppToggleCard
              active={forumEnabled}
              description="Turn this group into a Telegram-style forum with topics."
              onPress={() => onForumEnabledChange(!forumEnabled)}
              style={styles.toggleCard}
              title="Forum topics"
            />
          ) : null}
          <AppToggleCard
            active={joinRequiresApproval}
            description="New members must be approved by an admin before they can access this chat."
            onPress={() => onRestrictedJoinRequiresApprovalChange(!joinRequiresApproval)}
            style={styles.toggleCard}
            title="Join requests"
          />
          {(chat.chatType === "GROUP" || chat.chatType === "CHANNEL") ? (
            <AppToggleCard
              active={reactionsEnabled}
              description="Control whether members can react to posts and messages in this chat."
              onPress={() => onReactionsEnabledChange(!reactionsEnabled)}
              style={styles.toggleCard}
              title="Reactions"
            />
          ) : null}
          {chat.chatType === "CHANNEL" ? (
            <>
              <AppToggleCard
                active={commentsEnabled}
                description="Allow subscribers to open discussion threads for new channel posts."
                onPress={() => onCommentsEnabledChange(!commentsEnabled)}
                style={styles.toggleCard}
                title="Comments"
              />
              <AppToggleCard
                active={crossPostingEnabled}
                description="Mirror new channel posts into the linked discussion group as thread roots."
                onPress={() => onCrossPostingEnabledChange(!crossPostingEnabled)}
                style={styles.toggleCard}
                title="Cross-posting"
              />
              <View style={styles.selectorGroup}>
                <Text style={styles.selectorLabel}>Discussion group</Text>
                <Text style={styles.selectorHint}>
                  Link a group that will host channel comments and discussion threads.
                </Text>
                {availableDiscussionChats.length === 0 ? (
                  <Text style={styles.selectorEmpty}>No groups available in your chat list yet.</Text>
                ) : (
                  <View style={styles.selectorOptions}>
                    {availableDiscussionChats.map((item) => {
                      const selected = discussionChatId === item.chatId;
                      return (
                        <Pressable
                          key={item.chatId}
                          onPress={() => onDiscussionChatChange(item.chatId)}
                          style={[styles.selectorOption, selected && styles.selectorOptionActive]}
                        >
                          <Text
                            style={[
                              styles.selectorOptionText,
                              selected && styles.selectorOptionTextActive
                            ]}
                          >
                            {item.title}
                          </Text>
                        </Pressable>
                      );
                    })}
                  </View>
                )}
                {chat.linkedDiscussionChatTitle ? (
                  <Text style={styles.selectorCurrent}>
                    Current: {chat.linkedDiscussionChatTitle}
                  </Text>
                ) : null}
              </View>
            </>
          ) : null}
          <AppButton
            disabled={savingProfile}
            fullWidth
            onPress={onSaveProfile}
            variant="primary"
          >
            {savingProfile ? "Saving..." : "Save chat profile"}
          </AppButton>
        </SectionCard>
      ) : null}

      {canManageInviteLinks ? (
        <SectionCard title="Public username">
          <AppTextField
            autoCapitalize="none"
            autoCorrect={false}
            onChangeText={onPublicUsernameChange}
            placeholder="@public_name"
            style={styles.input}
            value={publicUsername}
          />
          <AppButton
            disabled={savingPublicUsername}
            fullWidth
            onPress={onSavePublicUsername}
            variant="primary"
          >
            {savingPublicUsername ? "Saving..." : "Save public username"}
          </AppButton>
          {publicChatShareUrl ? (
            <View style={styles.shareLinkSection}>
              <Text style={styles.inviteLinkMeta}>{publicChatShareUrl}</Text>
              <AppButton
                fullWidth
                onPress={() => handleShareUrl(publicChatShareUrl)}
                variant="secondary"
              >
                Share public link
              </AppButton>
            </View>
          ) : null}
        </SectionCard>
      ) : null}

      {canApproveJoinRequests && (joinRequiresApproval || joinRequests.length > 0) ? (
        <SectionCard
          description="Review pending approvals for public links, invite links, and discoverable usernames."
          title="Join requests"
        >
          {loadingJoinRequests ? <ActivityIndicator color={appColors.textPrimary} style={styles.loader} /> : null}
          {joinRequests.length === 0 && !loadingJoinRequests ? (
            <Text style={styles.selectorEmpty}>No pending join requests.</Text>
          ) : null}
          <View style={styles.inviteLinksList}>
            {joinRequests.map((request) => (
              <ModerationPersonCard
                key={request.userId}
                meta={request.username ? `@${request.username}` : request.phoneNumber ?? "phone-hidden"}
                photoUrl={request.photoUrl}
                subtitle={`Source: ${request.source} | ${new Date(request.requestedAt).toLocaleString()}`}
                title={request.displayName}
              >
                <View style={styles.actionsRow}>
                  <AppButton
                    disabled={processingJoinRequestUserId === request.userId}
                    onPress={() => onApproveJoinRequest(request.userId)}
                    size="sm"
                    variant="secondary"
                  >
                    Approve
                  </AppButton>
                  <AppButton
                    disabled={processingJoinRequestUserId === request.userId}
                    onPress={() => onDeclineJoinRequest(request.userId)}
                    size="sm"
                    variant="danger"
                  >
                    {processingJoinRequestUserId === request.userId ? "Working..." : "Decline"}
                  </AppButton>
                </View>
              </ModerationPersonCard>
            ))}
          </View>
        </SectionCard>
      ) : null}

      {canModerateMessages && restrictedMembersCount > 0 ? (
        <SectionCard title="Restricted members">
          <Text style={styles.selectorHint}>
            {restrictedMembersCount} member{restrictedMembersCount === 1 ? "" : "s"} currently in
            read-only mode.
          </Text>
        </SectionCard>
      ) : null}

      {canModerateMessages ? (
        <SectionCard
          description="Users banned from this chat cannot join by invite link or public username until unbanned."
          title="Banned users"
        >
          {loadingBans ? <ActivityIndicator color={appColors.textPrimary} style={styles.loader} /> : null}
          {bannedMembers.length === 0 && !loadingBans ? (
            <Text style={styles.selectorEmpty}>No banned users.</Text>
          ) : null}
          <View style={styles.inviteLinksList}>
            {bannedMembers.map((ban) => (
              <ModerationPersonCard
                key={ban.userId}
                meta={ban.username ? `@${ban.username}` : ban.phoneNumber ?? "phone-hidden"}
                photoUrl={ban.photoUrl}
                subtitle={
                  ban.bannedUntil
                    ? `Banned until ${new Date(ban.bannedUntil).toLocaleString()}`
                    : "Permanent ban"
                }
                title={ban.displayName}
              >
                {ban.reason ? <Text style={styles.inviteLinkMeta}>{ban.reason}</Text> : null}
                <View style={styles.actionsRow}>
                  <AppButton
                    disabled={unbanningUserId === ban.userId}
                    onPress={() => onUnbanMember(ban.userId)}
                    size="sm"
                    variant="secondary"
                  >
                    {unbanningUserId === ban.userId ? "Working..." : "Unban"}
                  </AppButton>
                </View>
              </ModerationPersonCard>
            ))}
          </View>
        </SectionCard>
      ) : null}

      {canManageInviteLinks ? (
        <SectionCard title="Invite links">
          <AppTextField
            onChangeText={onInviteLabelChange}
            placeholder="Label (optional)"
            style={styles.input}
            value={inviteLabel}
          />
          <AppTextField
            keyboardType="number-pad"
            onChangeText={onInviteUsageLimitChange}
            placeholder="Usage limit (optional)"
            style={styles.input}
            value={inviteUsageLimit}
          />
          <AppButton
            disabled={creatingInviteLink}
            fullWidth
            onPress={onCreateInviteLink}
            variant="primary"
          >
            {creatingInviteLink ? "Creating..." : "Create invite link"}
          </AppButton>

          {loadingInviteLinks ? <ActivityIndicator color={appColors.textPrimary} style={styles.loader} /> : null}

          <View style={styles.inviteLinksList}>
            {inviteLinks.map((link) => {
              const expired = isInviteLinkExpired(link);
              const limitReached = isInviteLinkLimitReached(link);
              const shareDisabled = expired || limitReached;

              return (
                <View key={link.inviteLinkId} style={styles.inviteLinkCard}>
                  <Text style={styles.inviteLinkTitle}>{link.label || "Invite link"}</Text>
                  <Text style={styles.inviteLinkMeta}>{link.shareUrl}</Text>
                  <Text style={styles.inviteLinkMeta}>
                    Uses: {link.usageCount}
                    {typeof link.usageLimit === "number" ? ` / ${link.usageLimit}` : ""}
                  </Text>
                  {link.expiresAt ? (
                    <Text style={styles.inviteLinkMeta}>
                      {expired ? "Expired" : "Expires"}: {new Date(link.expiresAt).toLocaleString()}
                    </Text>
                  ) : null}
                  {link.revoked ? (
                    <Text style={styles.inviteLinkRevoked}>Revoked</Text>
                  ) : (
                    <>
                      {expired ? (
                        <Text style={styles.inviteLinkExpired}>Expired</Text>
                      ) : limitReached ? (
                        <Text style={styles.inviteLinkLimitReached}>Limit reached</Text>
                      ) : null}
                      <View style={styles.actionsRow}>
                        {!shareDisabled ? (
                          <AppButton
                            onPress={() => handleShareUrl(link.shareUrl)}
                            size="sm"
                            variant="secondary"
                          >
                            Share link
                          </AppButton>
                        ) : null}
                        <AppButton
                          disabled={revokingInviteLinkId === link.inviteLinkId}
                          onPress={() => onRevokeInviteLink(link.inviteLinkId)}
                          size="sm"
                          variant="danger"
                        >
                          {revokingInviteLinkId === link.inviteLinkId ? "Revoking..." : "Revoke"}
                        </AppButton>
                      </View>
                    </>
                  )}
                </View>
              );
            })}
          </View>
        </SectionCard>
      ) : null}
    </View>
  );
}

type AnalyticsTileProps = {
  label: string;
  value: number;
};

function AnalyticsTile({
  label,
  value
}: AnalyticsTileProps) {
  return (
    <View style={styles.analyticsTile}>
      <Text style={styles.analyticsValue}>{value}</Text>
      <Text style={styles.analyticsLabel}>{label}</Text>
    </View>
  );
}

type ModerationPersonCardProps = {
  children: React.ReactNode;
  meta: string;
  photoUrl: string | null;
  subtitle: string;
  title: string;
};

function ModerationPersonCard({
  children,
  meta,
  photoUrl,
  subtitle,
  title
}: ModerationPersonCardProps) {
  return (
    <View style={styles.inviteLinkCard}>
      <View style={styles.memberHeader}>
        <Avatar uri={photoUrl} title={title} size={42} />
        <View style={styles.memberInfo}>
          <Text style={styles.memberName}>{title}</Text>
          <Text style={styles.memberMeta}>{meta}</Text>
          <Text style={styles.inviteLinkMeta}>{subtitle}</Text>
        </View>
      </View>
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  headerContent: {
    gap: appSpacing.lg
  },
  chatIdentityCard: {
    alignItems: "center"
  },
  chatIdentityText: {
    alignItems: "center",
    gap: appSpacing.xs
  },
  chatIdentityTitle: {
    fontSize: 20,
    fontWeight: "700",
    color: appColors.textPrimary
  },
  chatIdentityMeta: {
    color: appColors.textSecondary
  },
  photoActions: {
    width: "100%",
    gap: appSpacing.sm
  },
  chatActionGrid: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: appSpacing.sm
  },
  analyticsGrid: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: appSpacing.sm
  },
  analyticsTile: {
    minWidth: 108,
    flexGrow: 1,
    borderRadius: appRadii.md,
    backgroundColor: appColors.background,
    paddingHorizontal: appSpacing.md,
    paddingVertical: appSpacing.sm + 2,
    gap: appSpacing.xs
  },
  analyticsValue: {
    color: appColors.textPrimary,
    fontSize: 20,
    fontWeight: "700"
  },
  analyticsLabel: {
    color: appColors.textSecondary,
    fontSize: 12,
    fontWeight: "600"
  },
  input: {
    marginBottom: appSpacing.sm
  },
  aboutInput: {
    minHeight: 88,
    textAlignVertical: "top"
  },
  toggleCard: {
    marginBottom: appSpacing.sm
  },
  selectorGroup: {
    gap: appSpacing.sm,
    marginBottom: appSpacing.sm
  },
  selectorLabel: {
    color: appColors.textPrimary,
    fontWeight: "700"
  },
  selectorHint: {
    color: appColors.textSecondary,
    lineHeight: 18
  },
  selectorOptions: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: appSpacing.sm
  },
  selectorOption: {
    borderRadius: appRadii.pill,
    backgroundColor: appColors.surfaceAccent,
    paddingHorizontal: appSpacing.md,
    paddingVertical: appSpacing.sm
  },
  selectorOptionActive: {
    backgroundColor: appColors.textPrimary
  },
  selectorOptionText: {
    color: appColors.textPrimary,
    fontWeight: "600"
  },
  selectorOptionTextActive: {
    color: appColors.inverse
  },
  selectorEmpty: {
    color: appColors.textSecondary
  },
  selectorCurrent: {
    color: appColors.brandText,
    fontWeight: "600"
  },
  loader: {
    marginVertical: appSpacing.md
  },
  candidatesContent: {
    gap: appSpacing.md,
    paddingVertical: appSpacing.md
  },
  candidateCard: {
    width: 180,
    borderRadius: appRadii.md,
    backgroundColor: appColors.background,
    padding: appSpacing.md,
    alignItems: "center"
  },
  candidateCardSelected: {
    borderWidth: 2,
    borderColor: appColors.textPrimary
  },
  candidateName: {
    fontSize: 16,
    fontWeight: "600",
    color: appColors.textPrimary
  },
  candidateMeta: {
    marginTop: appSpacing.xs,
    color: appColors.textSecondary
  },
  inviteLinksList: {
    gap: appSpacing.sm,
    marginTop: appSpacing.md
  },
  inviteLinkCard: {
    borderRadius: appRadii.md,
    backgroundColor: appColors.background,
    padding: appSpacing.md,
    gap: appSpacing.xs
  },
  inviteLinkTitle: {
    color: appColors.textPrimary,
    fontWeight: "700"
  },
  inviteLinkMeta: {
    color: appColors.textSecondary,
    fontSize: 12
  },
  inviteLinkRevoked: {
    color: appColors.danger,
    fontWeight: "700",
    marginTop: appSpacing.sm - 2
  },
  inviteLinkExpired: {
    color: "#9a6700",
    fontWeight: "700",
    marginTop: appSpacing.sm - 2
  },
  inviteLinkLimitReached: {
    color: "#9a6700",
    fontWeight: "700",
    marginTop: appSpacing.sm - 2
  },
  shareLinkSection: {
    gap: appSpacing.sm,
    marginTop: appSpacing.md
  },
  memberHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    gap: appSpacing.md
  },
  memberInfo: {
    flex: 1
  },
  memberName: {
    fontSize: 18,
    fontWeight: "600",
    color: appColors.textPrimary
  },
  memberMeta: {
    marginTop: 2,
    color: appColors.textSecondary
  },
  actionsRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: appSpacing.sm
  }
});
