package com.alex.messenger.bot.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record BotApiDeleteMessageRequest(
        @NotNull UUID messageId
) {
}
