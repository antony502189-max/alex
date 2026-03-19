package com.alex.messenger.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record RequestPhoneChangeResponse(
        UUID challengeId,
        String newPhoneNumber,
        Instant expiresAt,
        String debugCode
) {
}
