package com.alex.messenger.message;

import java.time.Instant;
import java.util.UUID;

public record DirectMessageCreatedEvent(
        UUID chatId,
        UUID senderId,
        Instant createdAt
) {
}
