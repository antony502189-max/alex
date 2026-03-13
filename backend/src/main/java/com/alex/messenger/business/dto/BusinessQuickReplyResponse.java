package com.alex.messenger.business.dto;

import java.time.Instant;
import java.util.UUID;

public record BusinessQuickReplyResponse(
        UUID quickReplyId,
        String shortcut,
        String messageText,
        int position,
        Instant createdAt,
        Instant updatedAt
) {
}
