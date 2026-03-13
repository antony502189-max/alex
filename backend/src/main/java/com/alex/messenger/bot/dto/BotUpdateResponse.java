package com.alex.messenger.bot.dto;

import com.alex.messenger.message.dto.ChatMessageResponse;
import java.time.Instant;
import java.util.UUID;

public record BotUpdateResponse(
        long updateId,
        String updateType,
        UUID botUserId,
        UUID chatId,
        ChatMessageResponse message,
        BotCallbackQueryResponse callbackQuery,
        BotWebAppDataResponse webAppData,
        BotWebAppQueryResponse webAppQuery,
        BotPreCheckoutQueryResponse preCheckoutQuery,
        BotSuccessfulPaymentResponse successfulPayment,
        Instant createdAt
) {
}
