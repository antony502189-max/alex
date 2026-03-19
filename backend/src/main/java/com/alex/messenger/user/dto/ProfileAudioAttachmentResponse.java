package com.alex.messenger.user.dto;

import java.time.Instant;
import java.util.UUID;

public record ProfileAudioAttachmentResponse(
        UUID attachmentId,
        String originalFileName,
        String contentType,
        String kind,
        long fileSizeBytes,
        Long durationMs,
        Instant createdAt
) {
}
