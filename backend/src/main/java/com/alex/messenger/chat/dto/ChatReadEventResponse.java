package com.alex.messenger.chat.dto;

import java.time.Instant;
import java.util.UUID;

public record ChatReadEventResponse(
        UUID chatId,
        UUID userId,
        UUID messageId,
        Instant readAt
) {
}
