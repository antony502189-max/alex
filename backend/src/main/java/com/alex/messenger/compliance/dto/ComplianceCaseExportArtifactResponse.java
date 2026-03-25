package com.alex.messenger.compliance.dto;

import java.time.Instant;
import java.util.UUID;

public record ComplianceCaseExportArtifactResponse(
        UUID artifactId,
        String exportedByOperatorId,
        Instant exportedAt,
        int messageCount,
        String artifactChecksum,
        String contentType,
        Instant expiresAt,
        int downloadCount,
        Instant lastDownloadedAt,
        String lastDownloadedByOperatorId
) {
}
