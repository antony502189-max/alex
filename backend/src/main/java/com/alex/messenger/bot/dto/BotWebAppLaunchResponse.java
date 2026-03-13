package com.alex.messenger.bot.dto;

import java.time.Instant;
import java.util.UUID;

public record BotWebAppLaunchResponse(
        UUID botUserId,
        String botUsername,
        UUID chatId,
        String launchUrl,
        Instant issuedAt,
        Instant expiresAt
) {
}
