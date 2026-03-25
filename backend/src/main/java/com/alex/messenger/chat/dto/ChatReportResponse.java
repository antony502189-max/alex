package com.alex.messenger.chat.dto;

import java.time.Instant;
import java.util.UUID;

public record ChatReportResponse(
        UUID reportId,
        UUID chatId,
        String category,
        Instant createdAt
) {
}
