package com.alex.messenger.call.dto;

import java.time.Instant;
import java.util.UUID;

public record CallJoinLinkResponse(
        UUID linkId,
        UUID chatId,
        UUID createdByUserId,
        String kind,
        String mode,
        String label,
        String token,
        String shareUrl,
        boolean revoked,
        int usageCount,
        Instant expiresAt,
        Instant createdAt,
        Instant lastUsedAt
) {
}
