package com.alex.messenger.bot.dto;

import java.time.Instant;
import java.util.UUID;

public record DeveloperBotResponse(
        UUID botUserId,
        UUID ownerUserId,
        String displayName,
        String username,
        String description,
        String about,
        boolean supportsInline,
        String webAppUrl,
        String photoUrl,
        Instant photoAccessExpiresAt,
        String apiTokenPrefix,
        Instant tokenRotatedAt,
        String webhookUrl,
        boolean webhookEnabled,
        boolean hasWebhookSecret,
        Instant lastWebhookDeliveryAt,
        String lastWebhookError,
        Instant createdAt,
        Instant updatedAt
) {
}
