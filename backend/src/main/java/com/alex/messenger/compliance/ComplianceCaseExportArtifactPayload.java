package com.alex.messenger.compliance;

import com.alex.messenger.message.dto.ChatMessageResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ComplianceCaseExportArtifactPayload(
        UUID artifactId,
        UUID caseId,
        UUID targetUserId,
        String caseReference,
        String legalBasis,
        String reason,
        Instant fromInclusive,
        Instant toExclusive,
        String requestedByOperatorId,
        String approvedByOperatorId,
        String exportedByOperatorId,
        Instant exportedAt,
        List<ChatMessageResponse> messages
) {
}
