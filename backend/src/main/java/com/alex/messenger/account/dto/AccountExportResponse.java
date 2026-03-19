package com.alex.messenger.account.dto;

import java.time.Instant;
import java.util.UUID;

public record AccountExportResponse(
        UUID jobId,
        String status,
        String format,
        boolean includeAttachmentsMetadata,
        int messageCount,
        String artifactChecksum,
        String artifactLocation,
        Instant createdAt,
        Instant completedAt
) {
}
