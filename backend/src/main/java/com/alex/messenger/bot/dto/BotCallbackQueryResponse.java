package com.alex.messenger.bot.dto;

import java.time.Instant;
import java.util.UUID;

public record BotCallbackQueryResponse(
        UUID callbackQueryId,
        UUID botUserId,
        UUID chatId,
        UUID messageId,
        UUID fromUserId,
        UUID actionId,
        String callbackData,
        Instant createdAt,
        Instant answeredAt,
        String answerText,
        boolean showAlert,
        String redirectUrl
) {
}
