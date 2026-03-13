package com.alex.messenger.bot.dto;

import jakarta.validation.constraints.NotBlank;

public record ResolveBotWebAppRequest(
        @NotBlank String initData,
        @NotBlank String signature
) {
}
