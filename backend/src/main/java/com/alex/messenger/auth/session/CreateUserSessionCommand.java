package com.alex.messenger.auth.session;

import java.time.Instant;

public record CreateUserSessionCommand(
        String deviceName,
        String platform,
        String appVersion,
        String userAgent,
        String ipAddress,
        String authMethod,
        String refreshTokenHash,
        Instant refreshTokenExpiresAt,
        Boolean trustedSession,
        Instant trustedAt
) {
}
