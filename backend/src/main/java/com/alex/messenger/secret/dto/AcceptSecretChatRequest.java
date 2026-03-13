package com.alex.messenger.secret.dto;

import jakarta.validation.constraints.NotBlank;

public record AcceptSecretChatRequest(
        @NotBlank String recipientPublicKey,
        @NotBlank String sharedKeyFingerprint
) {
}
