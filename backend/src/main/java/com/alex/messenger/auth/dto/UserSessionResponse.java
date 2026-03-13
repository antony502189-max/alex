package com.alex.messenger.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record UserSessionResponse(
        UUID sessionId,
        String deviceName,
        String platform,
        String appVersion,
        String userAgent,
        String ipAddress,
        Instant createdAt,
        Instant lastActiveAt,
        boolean notificationsEnabled,
        boolean current,
        String authMethod,
        boolean trustedSession,
        Instant trustedAt
) {
}
