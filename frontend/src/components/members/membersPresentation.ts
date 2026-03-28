import type { ChatInviteLink, ChatMember, ChatSummary } from "../../types";
export { buildPublicChatShareUrl } from "../../services/chatLinks";

export function sortMembers(members: ChatMember[]) {
  const rank: Record<string, number> = {
    OWNER: 0,
    ADMIN: 1,
    MEMBER: 2
  };

  return [...members].sort((left, right) => {
    const leftRank = rank[left.role] ?? 99;
    const rightRank = rank[right.role] ?? 99;
    if (leftRank !== rightRank) {
      return leftRank - rightRank;
    }
    return left.displayName.localeCompare(right.displayName);
  });
}

export function getMemberPermissionLabels(
  member: ChatMember,
  chatType: ChatSummary["chatType"]
) {
  const labels: string[] = [];
  if (member.canManageMembers) {
    labels.push("Members");
  }
  if (member.canManageInviteLinks) {
    labels.push("Invite links");
  }
  if (member.canManageMessages) {
    labels.push("Moderation");
  }
  if (member.canPinMessages) {
    labels.push("Pins");
  }
  if (member.canApproveJoinRequests) {
    labels.push("Join requests");
  }
  if (member.anonymousAdmin) {
    labels.push("Anonymous");
  }
  if (chatType === "CHANNEL" && member.canPostMessages) {
    labels.push("Can post");
  }
  return labels;
}

export function getMembersChatTypeLabel(chatType: ChatSummary["chatType"]) {
  return chatType === "CHANNEL" ? "Channel" : "Group";
}

export function isInviteLinkExpired(link: ChatInviteLink, nowMs = Date.now()) {
  if (!link.expiresAt) {
    return false;
  }

  return new Date(link.expiresAt).getTime() <= nowMs;
}

export function isInviteLinkLimitReached(link: ChatInviteLink) {
  return typeof link.usageLimit === "number" && link.usageCount >= link.usageLimit;
}
