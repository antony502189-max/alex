package com.alex.messenger.media;

import java.time.Instant;

public record PhotoAccess(
        String photoUrl,
        Instant photoAccessExpiresAt
) {
}
