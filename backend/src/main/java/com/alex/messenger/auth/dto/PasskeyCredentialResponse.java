package com.alex.messenger.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record PasskeyCredentialResponse(
        UUID credentialId,
        String externalCredentialId,
        String label,
        String transports,
        Instant createdAt,
        Instant lastUsedAt
) {
}
