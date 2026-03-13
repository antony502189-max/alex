package com.alex.messenger.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record QrLoginChallengeResponse(
        UUID challengeId,
        String status,
        String deviceName,
        String platform,
        String appVersion,
        String ipAddress,
        String userAgent,
        Instant createdAt,
        Instant expiresAt,
        Instant boundAt,
        Instant approvedAt
) {
}
