package com.alex.messenger.chat.dto;

import java.time.Instant;
import java.util.UUID;

public record LeaveChatResponse(
        UUID chatId,
        UUID userId,
        String status,
        Instant leftAt
) {
}
