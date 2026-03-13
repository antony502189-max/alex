package com.alex.messenger.bot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BotApiMessageActionRequest(
        @NotBlank @Size(max = 16) String actionType,
        @NotBlank @Size(max = 64) String buttonText,
        @Size(max = 255) String callbackData,
        @Size(max = 512) String targetUrl,
        @Size(max = 128) String webAppStartParameter
) {
}
