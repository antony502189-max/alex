package com.alex.messenger.media;

import java.time.Instant;

public record PresignedMediaAccess(
        String downloadUrl,
        Instant expiresAt
) {
}
