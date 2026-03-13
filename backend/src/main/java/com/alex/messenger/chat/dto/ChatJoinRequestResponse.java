package com.alex.messenger.chat.dto;

import java.time.Instant;
import java.util.UUID;

public record ChatJoinRequestResponse(
        UUID userId,
        String phoneNumber,
        String displayName,
        String username,
        String photoUrl,
        Instant photoAccessExpiresAt,
        String source,
        UUID inviteLinkId,
        Instant requestedAt
) {
}
