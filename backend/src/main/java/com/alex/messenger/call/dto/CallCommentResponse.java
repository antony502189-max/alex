package com.alex.messenger.call.dto;

import java.time.Instant;
import java.util.UUID;

public record CallCommentResponse(
        UUID commentId,
        UUID callId,
        UUID chatId,
        UUID authorUserId,
        String authorDisplayName,
        String authorPhotoUrl,
        Instant authorPhotoAccessExpiresAt,
        String content,
        Instant createdAt
) {
}
