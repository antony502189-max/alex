package com.alex.messenger.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record AuthResponse(
        boolean authenticated,
        boolean requiresTwoFactor,
        String token,
        String refreshToken,
        UUID sessionId,
        UUID userId,
        String phoneNumber,
        String displayName,
        String username,
        Instant accessTokenExpiresAt,
        Instant refreshTokenExpiresAt,
        String authMethod,
        Boolean trustedSession,
        UUID twoFactorChallengeId,
        String twoFactorHint
) {
}
