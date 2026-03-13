package com.alex.messenger.user.dto;

import java.time.Instant;
import java.util.UUID;

public record BlockedUserResponse(
        UUID userId,
        String phoneNumber,
        String displayName,
        String username,
        boolean bot,
        String botDescription,
        boolean botSupportsInline,
        String botWebAppUrl,
        String photoUrl,
        Instant photoAccessExpiresAt,
        boolean online,
        Instant lastSeenAt,
        Instant blockedAt
) {
}
