package com.alex.messenger.story.dto;

import java.time.Instant;

public record StoryMediaResponse(
        String kind,
        String fileName,
        String contentType,
        Long durationMs,
        String downloadUrl,
        String previewUrl,
        Instant accessExpiresAt,
        boolean requiresAuthorization,
        boolean streamingSupported
) {
}
