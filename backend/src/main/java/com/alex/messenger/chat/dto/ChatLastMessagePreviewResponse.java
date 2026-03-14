package com.alex.messenger.chat.dto;

import java.time.Instant;
import java.util.UUID;

public record ChatLastMessagePreviewResponse(
        UUID messageId,
        UUID senderId,
        String senderDisplayName,
        boolean anonymousSender,
        boolean outgoing,
        String messageType,
        String previewText,
        Instant createdAt,
        Instant editedAt,
        Instant deletedAt
) {
}
