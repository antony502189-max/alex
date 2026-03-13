package com.alex.messenger.secret.dto;

import java.time.Instant;
import java.util.UUID;

public record SecretChatMessageResponse(
        UUID secretChatId,
        UUID secretMessageId,
        UUID senderUserId,
        UUID senderSessionId,
        String messageType,
        String ciphertext,
        String nonce,
        Instant createdAt,
        Instant readAt,
        Instant expiresAt
) {
}
