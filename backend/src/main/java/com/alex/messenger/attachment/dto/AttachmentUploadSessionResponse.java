package com.alex.messenger.attachment.dto;

import java.time.Instant;
import java.util.UUID;

public record AttachmentUploadSessionResponse(
        UUID uploadSessionId,
        UUID chatId,
        String originalFileName,
        String contentType,
        String kind,
        long totalSizeBytes,
        long uploadedBytes,
        int chunkSizeBytes,
        String status,
        boolean complete,
        Instant expiresAt,
        UUID completedAttachmentId,
        UUID albumId,
        Integer albumItemIndex
) {
}
