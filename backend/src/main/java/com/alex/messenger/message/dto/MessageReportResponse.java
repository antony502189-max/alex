package com.alex.messenger.message.dto;

import java.time.Instant;
import java.util.UUID;

public record MessageReportResponse(
        UUID reportId,
        UUID messageId,
        UUID chatId,
        String category,
        Instant createdAt
) {
}
