package com.alex.messenger.bot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BotApiInlineResultRequest(
        @NotBlank @Size(max = 64) String resultId,
        @NotBlank @Size(max = 120) String title,
        @Size(max = 255) String description,
        @NotBlank @Size(max = 4000) String text
) {
}
