package com.alex.messenger.bot.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record BotApiAnswerPreCheckoutQueryRequest(
        @NotNull UUID preCheckoutQueryId,
        @NotNull Boolean ok,
        @Size(max = 255) String text
) {
}
