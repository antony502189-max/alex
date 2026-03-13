package com.alex.messenger.chat.dto;

import java.time.Instant;
import java.util.UUID;

public record ChatBanResponse(
        UUID userId,
        String phoneNumber,
        String displayName,
        String username,
        String photoUrl,
        Instant photoAccessExpiresAt,
        Instant bannedAt,
        Instant bannedUntil,
        String reason,
        UUID bannedByUserId
) {
}
