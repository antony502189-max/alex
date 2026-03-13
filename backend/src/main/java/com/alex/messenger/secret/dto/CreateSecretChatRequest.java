package com.alex.messenger.secret.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateSecretChatRequest(
        @NotNull UUID recipientUserId,
        @NotBlank String initiatorPublicKey,
        Integer autoDeleteSeconds
) {
}
