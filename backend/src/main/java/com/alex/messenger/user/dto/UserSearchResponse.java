package com.alex.messenger.user.dto;

import java.util.UUID;

public record UserSearchResponse(
        UUID userId,
        String phoneNumber,
        String displayName,
        String username,
        boolean bot,
        String botDescription,
        boolean botSupportsInline,
        String botWebAppUrl,
        String photoUrl,
        java.time.Instant photoAccessExpiresAt,
        boolean online,
        java.time.Instant lastSeenAt
) {
}
