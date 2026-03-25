package com.alex.messenger.compliance.dto;

import com.alex.messenger.message.dto.ChatMessageResponse;
import java.time.Instant;
import java.util.List;

public record ComplianceCaseExportResponse(
        ComplianceCaseResponse caseInfo,
        ComplianceCaseExportArtifactResponse artifact,
        String exportedByOperatorId,
        Instant exportedAt,
        int messageCount,
        String artifactChecksum,
        List<ChatMessageResponse> messages
) {
    public ComplianceCaseExportResponse(
            ComplianceCaseResponse caseInfo,
            String exportedByOperatorId,
            Instant exportedAt,
            int messageCount,
            String artifactChecksum,
            List<ChatMessageResponse> messages
    ) {
        this(caseInfo, null, exportedByOperatorId, exportedAt, messageCount, artifactChecksum, messages);
    }
}
