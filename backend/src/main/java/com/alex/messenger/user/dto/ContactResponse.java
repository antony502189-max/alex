package com.alex.messenger.user.dto;

import java.util.UUID;

public record ContactResponse(
        UUID userId,
        String contactName,
        String displayName,
        String username,
        boolean bot,
        String botDescription,
        boolean botSupportsInline,
        String botWebAppUrl,
        String phoneNumber,
        String photoUrl,
        java.time.Instant photoAccessExpiresAt,
        boolean online,
        java.time.Instant lastSeenAt
) {
}
