package com.alex.messenger.bot.dto;

import java.time.Instant;
import java.util.UUID;

public record BotWebAppQueryResponse(
        UUID queryId,
        UUID botUserId,
        UUID chatId,
        UUID fromUserId,
        String startParameter,
        String platform,
        String queryText,
        Instant createdAt,
        Instant answeredAt,
        UUID resultMessageId
) {
}
