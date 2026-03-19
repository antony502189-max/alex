package com.alex.messenger.bot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResolveBotWebAppRequest(
        @NotBlank @Size(max = 8192) String initData,
        @NotBlank @Size(max = 512) String signature
) {
}
