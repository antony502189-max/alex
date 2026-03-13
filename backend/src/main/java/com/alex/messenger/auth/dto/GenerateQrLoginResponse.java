package com.alex.messenger.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record GenerateQrLoginResponse(
        UUID challengeId,
        String qrToken,
        Instant createdAt,
        Instant expiresAt
) {
}
