package com.alex.messenger.chat.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;

public record UpdateMemberPermissionsRequest(
        Boolean canManageMembers,
        Boolean canManageInviteLinks,
        Boolean canManageMessages,
        Boolean canPinMessages,
        Boolean canApproveJoinRequests,
        Boolean canPostMessages,
        Boolean anonymousAdmin
) {

    @AssertTrue(message = "No member permission changes were provided")
    @JsonIgnore
    public boolean isChangeRequested() {
        return canManageMembers != null
                || canManageInviteLinks != null
                || canManageMessages != null
                || canPinMessages != null
                || canApproveJoinRequests != null
                || canPostMessages != null
                || anonymousAdmin != null;
    }
}
