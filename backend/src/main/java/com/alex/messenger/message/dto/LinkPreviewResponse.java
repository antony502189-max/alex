package com.alex.messenger.message.dto;

import java.time.Instant;

public record LinkPreviewResponse(
        String url,
        String canonicalUrl,
        String title,
        String description,
        String siteName,
        String imageUrl,
        Instant fetchedAt,
        Instant expiresAt
) {
}
