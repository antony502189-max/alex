package com.alex.messenger.bot.dto;

import java.time.Instant;
import java.util.UUID;

public record BotWebAppDataResponse(
        UUID eventId,
        UUID botUserId,
        UUID chatId,
        UUID messageId,
        UUID fromUserId,
        String buttonText,
        String data,
        String startParameter,
        String platform,
        Instant createdAt
) {
}
