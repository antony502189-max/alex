package com.alex.messenger.chat.dto;

import java.time.Instant;
import java.util.UUID;

public record ChatInviteLinkResponse(
        UUID inviteLinkId,
        UUID chatId,
        String label,
        String token,
        String shareUrl,
        boolean revoked,
        Integer usageLimit,
        int usageCount,
        Instant expiresAt,
        Instant createdAt,
        Instant lastUsedAt
) {
}
