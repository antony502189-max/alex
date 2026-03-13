package com.alex.messenger.call.dto;

import java.time.Instant;
import java.util.UUID;

public record CallParticipantResponse(
        UUID userId,
        String displayName,
        String phoneNumber,
        String photoUrl,
        Instant photoAccessExpiresAt,
        String state,
        Instant invitedAt,
        Instant joinedAt,
        Instant leftAt,
        boolean audioPublishingAllowed,
        boolean videoPublishingAllowed,
        boolean screenShareAllowed,
        boolean screenSharing,
        boolean handRaised,
        boolean audioMuted,
        boolean mutedByModerator,
        UUID mutedByUserId,
        Instant mutedAt,
        UUID moderatedByUserId,
        Instant moderatedAt
) {
}
