package com.alex.messenger.attachment.dto;

import java.util.List;
import java.util.UUID;

public record CreateAttachmentUploadSessionRequest(
        UUID chatId,
        String originalFileName,
        String contentType,
        String kind,
        long totalSizeBytes,
        Long durationMs,
        Integer width,
        Integer height,
        List<Integer> waveform,
        UUID albumId,
        Integer albumItemIndex
) {
}
