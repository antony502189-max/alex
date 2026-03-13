package com.alex.messenger.chat.dto;

import java.time.Instant;
import java.util.UUID;

public record ChatMemberResponse(
        UUID userId,
        String phoneNumber,
        String displayName,
        String photoUrl,
        Instant photoAccessExpiresAt,
        String role,
        Instant joinedAt,
        Instant lastReadAt,
        Instant lastSentMessageAt,
        boolean canSendMessages,
        boolean canManageMembers,
        boolean canManageInviteLinks,
        boolean canManageMessages,
        boolean canPinMessages,
        boolean canApproveJoinRequests,
        boolean canPostMessages,
        boolean anonymousAdmin,
        Instant restrictedUntil,
        String restrictionReason
) {
}
