package com.alex.messenger.bot.dto;

import java.util.UUID;

public record BotInlineResultResponse(
        String resultId,
        UUID botUserId,
        String botUsername,
        String title,
        String description,
        String text
) {
}
