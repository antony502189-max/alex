package com.alex.messenger.compliance.dto;

import java.time.Instant;
import java.util.UUID;

public record ComplianceCaseEventResponse(
        UUID eventId,
        String actorOperatorId,
        String eventType,
        String summary,
        Instant createdAt
) {
}
