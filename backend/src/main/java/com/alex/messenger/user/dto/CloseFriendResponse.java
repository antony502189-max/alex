package com.alex.messenger.user.dto;

import java.time.Instant;
import java.util.UUID;

public record CloseFriendResponse(
        UUID userId,
        String displayName,
        String username,
        String photoUrl,
        Instant photoAccessExpiresAt,
        Instant addedAt
) {
}
