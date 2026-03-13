package com.alex.messenger.call.dto;

import java.time.Instant;
import java.util.UUID;

public record CallHistoryEntryResponse(
        UUID callId,
        UUID chatId,
        String chatType,
        String title,
        String photoUrl,
        Instant photoAccessExpiresAt,
        String kind,
        String mode,
        String status,
        String direction,
        boolean missed,
        int participantCount,
        Instant startedAt,
        Instant answeredAt,
        Instant endedAt
) {
}
