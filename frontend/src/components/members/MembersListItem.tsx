import React from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";
import { Avatar } from "../Avatar";
import { AppButton } from "../ui/AppButton";
import { AppToggleCard } from "../ui/AppToggleCard";
import { appColors, appRadii, appSpacing } from "../../theme/tokens";
import type { ChatMember, ChatSummary } from "../../types";
import { getMemberPermissionLabels } from "./membersPresentation";

type PermissionPayload = {
  canManageMembers?: boolean;
  canManageInviteLinks?: boolean;
  canManageMessages?: boolean;
  canPinMessages?: boolean;
  canApproveJoinRequests?: boolean;
  canPostMessages?: boolean;
  anonymousAdmin?: boolean;
};

type MembersListItemProps = {
  banningUserId: string | null;
  canManageMembers: boolean;
  canModerateMessages: boolean;
  chatType: ChatSummary["chatType"];
  currentUserId: string;
  member: ChatMember;
  presenceLabel?: string | null;
  onBanMember: (userId: string) => void;
  onPermissionChange: (userId: string, payload: PermissionPayload) => void;
  onRemoveMember: (userId: string) => void;
  onRestrictionChange: (
    userId: string,
    nextCanSendMessages: boolean,
    restrictedUntil?: string | null,
    restrictionReason?: string | null
  ) => void;
  onRoleChange: (userId: string, role: "ADMIN" | "MEMBER") => void;
  restrictingUserId: string | null;
  updatingPermissionsUserId: string | null;
};

export function MembersListItem({
  banningUserId,
  canManageMembers,
  canModerateMessages,
  chatType,
  currentUserId,
  member,
  presenceLabel,
  onBanMember,
  onPermissionChange,
  onRemoveMember,
  onRestrictionChange,
  onRoleChange,
  restrictingUserId,
  updatingPermissionsUserId
}: MembersListItemProps) {
  const isOwner = member.role === "OWNER";
  const isSelf = member.userId === currentUserId;
  const canMutateMember = canManageMembers && !isOwner && !isSelf;
  const canRestrictMember = canModerateMessages && !isOwner && !isSelf;
  const canEditPermissions = canManageMembers && !isOwner && !isSelf;
  const memberRestricted = !member.canSendMessages;
  const restrictionActive = restrictingUserId === member.userId;
  const permissionUpdateActive = updatingPermissionsUserId === member.userId;
  const permissionLabels = getMemberPermissionLabels(member, chatType);

  return (
    <View style={styles.memberCard}>
      <View style={styles.memberHeader}>
        <Avatar uri={member.photoUrl} title={member.displayName} size={48} />
        <View style={styles.memberInfo}>
          <Text style={styles.memberName}>{member.displayName}</Text>
          <Text style={styles.memberMeta}>{member.phoneNumber ?? "phone-hidden"}</Text>
          {presenceLabel ? <Text style={styles.memberPresence}>{presenceLabel}</Text> : null}
          <Text style={styles.memberRole}>{member.role}</Text>
        </View>
        {isSelf ? <Text style={styles.meBadge}>You</Text> : null}
      </View>

      {permissionLabels.length > 0 ? (
        <View style={styles.permissionBadges}>
          {permissionLabels.map((label) => (
            <View key={`${member.userId}-${label}`} style={styles.permissionBadge}>
              <Text style={styles.permissionBadgeText}>{label}</Text>
            </View>
          ))}
        </View>
      ) : null}

      {memberRestricted ? (
        <View style={styles.restrictionCard}>
          <Text style={styles.restrictionTitle}>Read-only mode</Text>
          <Text style={styles.restrictionText}>
            {member.restrictedUntil
              ? `Until ${new Date(member.restrictedUntil).toLocaleString()}`
              : "No end time"}
          </Text>
          {member.restrictionReason ? (
            <Text style={styles.restrictionText}>{member.restrictionReason}</Text>
          ) : null}
        </View>
      ) : null}

      {canEditPermissions && member.role === "ADMIN" ? (
        <View style={styles.permissionEditor}>
          <Text style={styles.permissionTitle}>Admin rights</Text>
          <View style={styles.selectorOptions}>
            <PermissionChip
              active={member.canManageMembers}
              disabled={permissionUpdateActive}
              label="Members"
              onPress={() =>
                onPermissionChange(member.userId, {
                  canManageMembers: !member.canManageMembers
                })
              }
            />
            <PermissionChip
              active={member.canManageInviteLinks}
              disabled={permissionUpdateActive}
              label="Invite links"
              onPress={() =>
                onPermissionChange(member.userId, {
                  canManageInviteLinks: !member.canManageInviteLinks
                })
              }
            />
            <PermissionChip
              active={member.canManageMessages}
              disabled={permissionUpdateActive}
              label="Moderate"
              onPress={() =>
                onPermissionChange(member.userId, {
                  canManageMessages: !member.canManageMessages
                })
              }
            />
            <PermissionChip
              active={member.canPinMessages}
              disabled={permissionUpdateActive}
              label="Pins"
              onPress={() =>
                onPermissionChange(member.userId, {
                  canPinMessages: !member.canPinMessages
                })
              }
            />
            <PermissionChip
              active={member.canApproveJoinRequests}
              disabled={permissionUpdateActive}
              label="Join requests"
              onPress={() =>
                onPermissionChange(member.userId, {
                  canApproveJoinRequests: !member.canApproveJoinRequests
                })
              }
            />
            <PermissionChip
              active={member.anonymousAdmin}
              disabled={permissionUpdateActive}
              label="Anonymous"
              onPress={() =>
                onPermissionChange(member.userId, {
                  anonymousAdmin: !member.anonymousAdmin
                })
              }
            />
          </View>
        </View>
      ) : null}

      {canEditPermissions && chatType === "CHANNEL" ? (
        <View style={styles.permissionEditor}>
          <Text style={styles.permissionTitle}>Posting rights</Text>
          <AppToggleCard
            active={Boolean(member.canPostMessages)}
            activeLabel="POST"
            description="Toggle whether this member can publish posts to the channel feed."
            disabled={permissionUpdateActive}
            onPress={() =>
              onPermissionChange(member.userId, {
                canPostMessages: !member.canPostMessages
              })
            }
            inactiveLabel="READ"
            style={styles.toggleCard}
            title={member.canPostMessages ? "Can publish in channel" : "Read-only subscriber"}
          />
        </View>
      ) : null}

      {canMutateMember || canRestrictMember ? (
        <View style={styles.actionsRow}>
          {canMutateMember && member.role === "MEMBER" ? (
            <AppButton
              onPress={() => onRoleChange(member.userId, "ADMIN")}
              size="sm"
              variant="secondary"
            >
              Promote
            </AppButton>
          ) : null}

          {canMutateMember && member.role !== "MEMBER" ? (
            <AppButton
              onPress={() => onRoleChange(member.userId, "MEMBER")}
              size="sm"
              variant="secondary"
            >
              Demote
            </AppButton>
          ) : null}

          {canRestrictMember ? (
            <AppButton
              disabled={restrictionActive}
              onPress={() =>
                onRestrictionChange(
                  member.userId,
                  memberRestricted,
                  memberRestricted
                    ? null
                    : new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(),
                  memberRestricted ? null : "Posting restricted by admin"
                )
              }
              size="sm"
              variant="secondary"
            >
              {restrictionActive
                ? "Updating..."
                : memberRestricted
                  ? "Unrestrict"
                  : "Restrict 24h"}
            </AppButton>
          ) : null}

          {canRestrictMember ? (
            <AppButton
              disabled={banningUserId === member.userId}
              onPress={() => onBanMember(member.userId)}
              size="sm"
              variant="danger"
            >
              {banningUserId === member.userId ? "Banning..." : "Ban 7d"}
            </AppButton>
          ) : null}

          {canMutateMember ? (
            <AppButton
              onPress={() => onRemoveMember(member.userId)}
              size="sm"
              variant="danger"
            >
              Remove
            </AppButton>
          ) : null}
        </View>
      ) : null}
    </View>
  );
}

type PermissionChipProps = {
  active: boolean;
  disabled: boolean;
  label: string;
  onPress: () => void;
};

function PermissionChip({
  active,
  disabled,
  label,
  onPress
}: PermissionChipProps) {
  return (
    <Pressable
      disabled={disabled}
      onPress={onPress}
      style={[
        styles.selectorOption,
        active && styles.selectorOptionActive,
        disabled && styles.buttonDisabled
      ]}
    >
      <Text
        style={[styles.selectorOptionText, active && styles.selectorOptionTextActive]}
      >
        {label}
      </Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  memberCard: {
    borderRadius: appRadii.lg,
    backgroundColor: appColors.surface,
    padding: appSpacing.lg,
    gap: appSpacing.sm
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
  memberPresence: {
    color: "#0f766e",
    marginTop: 2
  },
  memberRole: {
    marginTop: appSpacing.sm - 2,
    color: "#0f766e",
    fontWeight: "700"
  },
  meBadge: {
    alignSelf: "flex-start",
    borderRadius: appRadii.pill,
    backgroundColor: appColors.surfaceAccent,
    color: appColors.brandText,
    overflow: "hidden",
    paddingHorizontal: 10,
    paddingVertical: 6,
    fontWeight: "700"
  },
  permissionBadges: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: appSpacing.sm
  },
  permissionBadge: {
    borderRadius: appRadii.pill,
    backgroundColor: appColors.surfaceAccent,
    paddingHorizontal: 10,
    paddingVertical: 6
  },
  permissionBadgeText: {
    color: appColors.textPrimary,
    fontSize: 12,
    fontWeight: "600"
  },
  restrictionCard: {
    borderRadius: appRadii.md,
    backgroundColor: "#fff7ed",
    padding: appSpacing.md,
    gap: appSpacing.xs
  },
  restrictionTitle: {
    color: "#9a3412",
    fontWeight: "700"
  },
  restrictionText: {
    color: "#9a3412",
    lineHeight: 18
  },
  permissionEditor: {
    gap: appSpacing.sm
  },
  permissionTitle: {
    color: appColors.textSecondary,
    fontWeight: "700"
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
  buttonDisabled: {
    opacity: 0.6
  },
  toggleCard: {
    marginBottom: appSpacing.sm
  },
  actionsRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: appSpacing.sm
  }
});
