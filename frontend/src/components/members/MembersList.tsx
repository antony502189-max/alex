import React from "react";
import { FlatList, StyleSheet } from "react-native";
import { formatPresenceStatus } from "../../services/presence";
import { appSpacing } from "../../theme/tokens";
import type { ChatMember, ChatSummary, UserPresenceStatus } from "../../types";
import { MembersListItem } from "./MembersListItem";

type PermissionPayload = {
  canManageMembers?: boolean;
  canManageInviteLinks?: boolean;
  canManageMessages?: boolean;
  canPinMessages?: boolean;
  canApproveJoinRequests?: boolean;
  canPostMessages?: boolean;
  anonymousAdmin?: boolean;
};

type MembersListProps = {
  banningUserId: string | null;
  canManageMembers: boolean;
  canModerateMessages: boolean;
  chatType: ChatSummary["chatType"];
  currentUserId: string;
  headerContent: React.ReactElement;
  memberPresenceByUserId: Record<string, UserPresenceStatus>;
  members: ChatMember[];
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

export function MembersList({
  banningUserId,
  canManageMembers,
  canModerateMessages,
  chatType,
  currentUserId,
  headerContent,
  memberPresenceByUserId,
  members,
  onBanMember,
  onPermissionChange,
  onRemoveMember,
  onRestrictionChange,
  onRoleChange,
  restrictingUserId,
  updatingPermissionsUserId
}: MembersListProps) {
  return (
    <FlatList
      contentContainerStyle={styles.listContent}
      data={members}
      keyExtractor={(item) => item.userId}
      ListHeaderComponent={headerContent}
      renderItem={({ item }) => (
        <MembersListItem
          banningUserId={banningUserId}
          canManageMembers={canManageMembers}
          canModerateMessages={canModerateMessages}
          chatType={chatType}
          currentUserId={currentUserId}
          member={item}
          presenceLabel={
            memberPresenceByUserId[item.userId]
              ? formatPresenceStatus(memberPresenceByUserId[item.userId], "status hidden")
              : null
          }
          onBanMember={onBanMember}
          onPermissionChange={onPermissionChange}
          onRemoveMember={onRemoveMember}
          onRestrictionChange={onRestrictionChange}
          onRoleChange={onRoleChange}
          restrictingUserId={restrictingUserId}
          updatingPermissionsUserId={updatingPermissionsUserId}
        />
      )}
    />
  );
}

const styles = StyleSheet.create({
  listContent: {
    gap: appSpacing.md,
    paddingBottom: appSpacing.xl
  }
});
