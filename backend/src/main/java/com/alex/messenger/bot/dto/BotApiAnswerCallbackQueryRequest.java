package com.alex.messenger.bot.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record BotApiAnswerCallbackQueryRequest(
        @NotNull UUID callbackQueryId,
        @Size(max = 255) String text,
        Boolean showAlert,
        @Size(max = 512) String redirectUrl
) {
}
