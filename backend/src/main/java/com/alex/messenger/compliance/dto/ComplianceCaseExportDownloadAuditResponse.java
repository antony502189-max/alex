package com.alex.messenger.compliance.dto;

import java.time.Instant;
import java.util.UUID;

public record ComplianceCaseExportDownloadAuditResponse(
        UUID auditId,
        UUID artifactId,
        UUID caseId,
        String operatorId,
        Instant downloadedAt,
        boolean checksumVerified
) {
}
