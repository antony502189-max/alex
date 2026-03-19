package com.alex.messenger.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record PasskeyLoginOptionsResponse(
        UUID challengeId,
        String challenge,
        UUID userId,
        String phoneNumber,
        Instant expiresAt
) {
}
