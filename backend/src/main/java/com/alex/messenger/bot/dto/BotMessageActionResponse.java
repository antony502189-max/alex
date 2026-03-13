package com.alex.messenger.bot.dto;

import java.time.Instant;
import java.util.UUID;

public record BotMessageActionResponse(
        UUID actionId,
        UUID botUserId,
        UUID messageId,
        String actionType,
        String buttonText,
        String callbackData,
        String targetUrl,
        String webAppStartParameter,
        UUID paymentInvoiceId,
        int sortOrder,
        Instant createdAt
) {
}
