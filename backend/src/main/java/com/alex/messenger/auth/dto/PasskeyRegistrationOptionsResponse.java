package com.alex.messenger.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record PasskeyRegistrationOptionsResponse(
        UUID challengeId,
        String challenge,
        UUID userId,
        String userName,
        String displayName,
        Instant expiresAt
) {
}
