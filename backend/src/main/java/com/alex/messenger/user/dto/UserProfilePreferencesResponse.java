package com.alex.messenger.user.dto;

import java.time.Instant;
import java.util.UUID;

public record UserProfilePreferencesResponse(
        UUID userId,
        String defaultProfileTab,
        Instant updatedAt
) {
}
