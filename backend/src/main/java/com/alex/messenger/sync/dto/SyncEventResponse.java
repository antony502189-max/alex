package com.alex.messenger.sync.dto;

import java.time.Instant;
import java.util.UUID;

public record SyncEventResponse(
        long cursor,
        String eventType,
        String canonicalEventType,
        String legacyEventType,
        boolean transitionLegacyEvent,
        String entityType,
        UUID entityId,
        UUID chatId,
        String payloadJson,
        Instant createdAt
) {
}
