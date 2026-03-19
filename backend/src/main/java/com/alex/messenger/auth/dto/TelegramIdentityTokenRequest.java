package com.alex.messenger.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TelegramIdentityTokenRequest(
        @NotBlank @Size(max = 120) String appId,
        @Size(max = 512) String redirectUri,
        @Size(max = 255) String state
) {
}
