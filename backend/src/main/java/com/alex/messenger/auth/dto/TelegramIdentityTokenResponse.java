package com.alex.messenger.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record TelegramIdentityTokenResponse(
        String token,
        Instant expiresAt,
        String appId,
        String redirectUri,
        String state,
        UUID userId,
        String phoneNumber,
        String displayName,
        String username
) {
}
