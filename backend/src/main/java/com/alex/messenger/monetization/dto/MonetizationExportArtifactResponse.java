package com.alex.messenger.monetization.dto;

import java.time.Instant;
import java.util.UUID;

public record MonetizationExportArtifactResponse(
        UUID artifactId,
        UUID channelChatId,
        UUID generatedByUserId,
        String artifactType,
        String format,
        String fileName,
        int rowCount,
        long totalUnits,
        String checksum,
        Instant generatedAt,
        String content
) {
}
