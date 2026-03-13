package com.alex.messenger.bot.dto;

import jakarta.validation.constraints.Size;

public record UpdateDeveloperBotRequest(
        @Size(max = 120) String displayName,
        @Size(min = 4, max = 64) String username,
        @Size(max = 255) String description,
        @Size(max = 255) String about,
        Boolean supportsInline,
        @Size(max = 512) String webAppUrl
) {
}
