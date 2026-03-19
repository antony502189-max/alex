package com.alex.messenger.bot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendBotWebAppDataRequest(
        @NotBlank @Size(max = 8192) String initData,
        @NotBlank @Size(max = 512) String signature,
        @NotBlank @Size(max = 4096) String data,
        @Size(max = 64) String buttonText
) {
}
