package com.alex.messenger.bot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BotApiCommandRequest(
        @NotBlank @Size(max = 33) String command,
        @NotBlank @Size(max = 255) String description
) {
}
