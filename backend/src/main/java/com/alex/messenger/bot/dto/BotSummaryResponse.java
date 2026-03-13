package com.alex.messenger.bot.dto;

import java.time.Instant;
import java.util.UUID;

public record BotSummaryResponse(
        UUID userId,
        String displayName,
        String username,
        String description,
        boolean supportsInline,
        String webAppUrl,
        String photoUrl,
        Instant photoAccessExpiresAt
) {
}
