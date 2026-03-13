package com.alex.messenger.bot.dto;

import java.time.Instant;
import java.util.UUID;

public record BotWebhookInfoResponse(
        UUID botUserId,
        String webhookUrl,
        boolean webhookEnabled,
        boolean hasSecretToken,
        Instant lastWebhookDeliveryAt,
        String lastWebhookError,
        Instant updatedAt
) {
}
