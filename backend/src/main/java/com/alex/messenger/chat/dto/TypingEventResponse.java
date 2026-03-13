package com.alex.messenger.chat.dto;

import java.time.Instant;
import java.util.UUID;

public record TypingEventResponse(
        UUID chatId,
        UUID userId,
        boolean typing,
        Instant emittedAt
) {
}
