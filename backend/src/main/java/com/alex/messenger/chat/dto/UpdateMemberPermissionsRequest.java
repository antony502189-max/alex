package com.alex.messenger.chat.dto;

public record UpdateMemberPermissionsRequest(
        Boolean canManageMembers,
        Boolean canManageInviteLinks,
        Boolean canManageMessages,
        Boolean canPinMessages,
        Boolean canApproveJoinRequests,
        Boolean canPostMessages,
        Boolean anonymousAdmin
) {
}
