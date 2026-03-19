package com.alex.messenger.user.dto;

import java.time.Instant;
import java.util.UUID;

public record UserPresenceResponse(
        UUID userId,
        boolean online,
        Instant lastSeenAt,
        String visibility,
        String statusText
) {
}
