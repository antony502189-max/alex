package com.alex.messenger.auth.dto;

import java.time.Instant;

public record TwoFactorStatusResponse(
        boolean enabled,
        String hint,
        Instant enabledAt
) {
}
