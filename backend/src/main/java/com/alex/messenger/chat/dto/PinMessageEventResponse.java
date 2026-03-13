package com.alex.messenger.chat.dto;

import java.time.Instant;
import java.util.UUID;

public record PinMessageEventResponse(
        UUID chatId,
        UUID messageId,
        UUID pinnedByUserId,
        Instant pinnedAt
) {
}
