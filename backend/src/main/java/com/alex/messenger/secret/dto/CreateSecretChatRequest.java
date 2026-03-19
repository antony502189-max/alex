package com.alex.messenger.secret.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

public record CreateSecretChatRequest(
        @NotNull UUID recipientUserId,
        @NotBlank String initiatorPublicKey,
        @PositiveOrZero @Max(604_800) Integer autoDeleteSeconds
) {
}
