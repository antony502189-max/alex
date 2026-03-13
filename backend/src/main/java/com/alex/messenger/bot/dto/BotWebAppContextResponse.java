package com.alex.messenger.bot.dto;

import java.time.Instant;
import java.util.UUID;

public record BotWebAppContextResponse(
        UUID userId,
        String displayName,
        String username,
        UUID botUserId,
        String botUsername,
        UUID chatId,
        String startParameter,
        String platform,
        Instant issuedAt,
        Instant expiresAt
) {
}
