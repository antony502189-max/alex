package com.alex.messenger.bot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDeveloperBotRequest(
        @NotBlank @Size(max = 120) String displayName,
        @NotBlank @Size(min = 4, max = 64) String username,
        @Size(max = 255) String description,
        @Size(max = 255) String about,
        boolean supportsInline,
        @Size(max = 512) String webAppUrl
) {
}
