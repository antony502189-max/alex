package com.alex.messenger.message.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MessageAttachmentResponse(
        UUID attachmentId,
        String originalFileName,
        String contentType,
        String kind,
        long fileSizeBytes,
        Long durationMs,
        String downloadUrl,
        String previewUrl,
        String thumbnailUrl,
        Integer width,
        Integer height,
        List<Integer> waveform,
        Instant accessExpiresAt,
        boolean requiresAuthorization,
        boolean streamingSupported,
        boolean voiceNote,
        boolean roundMessage,
        UUID albumId,
        Integer albumItemIndex,
        String moderationStatus,
        String moderationReason,
        boolean sensitiveContent,
        boolean blockedByModeration,
        UUID sourceAttachmentId,
        Long trimStartMs,
        Long trimEndMs,
        boolean hdPhoto
) {
}
