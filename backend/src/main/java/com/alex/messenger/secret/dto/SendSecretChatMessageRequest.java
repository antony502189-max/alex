package com.alex.messenger.secret.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

public record SendSecretChatMessageRequest(
        @NotBlank String ciphertext,
        @NotBlank String nonce,
        List<UUID> attachmentIds
) {
}
