package com.alex.messenger.bot.dto;

import java.time.Instant;
import java.util.UUID;

public record BotApiProfileResponse(
        UUID botUserId,
        String displayName,
        String username,
        String description,
        String about,
        boolean supportsInline,
        String webAppUrl,
        Instant tokenRotatedAt
) {
}
