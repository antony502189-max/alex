package com.alex.messenger.business.dto;

import java.time.Instant;
import java.util.UUID;

public record BusinessChatTagResponse(
        UUID tagId,
        UUID chatId,
        String tagName,
        String color,
        int position,
        Instant createdAt
) {
}
