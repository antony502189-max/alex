package com.alex.messenger.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record AuthSecurityEventResponse(
        UUID eventId,
        UUID userId,
        UUID sessionId,
        String eventType,
        String severity,
        String ipAddress,
        String userAgent,
        String deviceName,
        String platform,
        String appVersion,
        String details,
        Instant createdAt
) {
}
