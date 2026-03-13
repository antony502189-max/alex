package com.alex.messenger.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record RequestLoginCodeResponse(
        UUID challengeId,
        String phoneNumber,
        Instant expiresAt,
        int codeLength,
        String debugCode
) {
}
