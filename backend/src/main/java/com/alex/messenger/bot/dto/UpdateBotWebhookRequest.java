package com.alex.messenger.bot.dto;

import jakarta.validation.constraints.Size;

public record UpdateBotWebhookRequest(
        @Size(max = 512) String webhookUrl,
        @Size(max = 255) String secretToken
) {
}
