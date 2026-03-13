package com.alex.messenger.compliance.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ComplianceCaseResponse(
        UUID caseId,
        UUID targetUserId,
        String caseReference,
        String legalBasis,
        String reason,
        Instant fromInclusive,
        Instant toExclusive,
        String status,
        String requestedByOperatorId,
        Instant createdAt,
        String approvedByOperatorId,
        Instant approvedAt,
        String lastExportedByOperatorId,
        Instant lastExportedAt,
        int exportCount,
        String latestArtifactChecksum,
        List<ComplianceCaseEventResponse> events
) {
}
