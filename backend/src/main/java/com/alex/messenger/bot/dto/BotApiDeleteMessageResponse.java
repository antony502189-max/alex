package com.alex.messenger.bot.dto;

import java.util.UUID;

public record BotApiDeleteMessageResponse(
        UUID messageId,
        boolean deleted
) {
}
