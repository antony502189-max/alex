package com.alex.messenger.call.dto;

import java.time.Instant;
import java.util.UUID;

public record CallSignalEventResponse(
        UUID callId,
        UUID fromUserId,
        UUID toUserId,
        String signalType,
        String payload,
        Instant emittedAt
) {
}
