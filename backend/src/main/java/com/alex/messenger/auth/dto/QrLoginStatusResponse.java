package com.alex.messenger.auth.dto;

import java.time.Instant;

public record QrLoginStatusResponse(
        String status,
        Instant expiresAt,
        String deviceName,
        String platform,
        String appVersion,
        AuthResponse auth
) {
}
