package com.alex.messenger.compliance.dto;

import com.alex.messenger.message.dto.ChatMessageResponse;
import java.time.Instant;
import java.util.List;

public record ComplianceCaseArtifactDownloadResponse(
        ComplianceCaseResponse caseInfo,
        ComplianceCaseExportArtifactResponse artifact,
        String downloadedByOperatorId,
        Instant downloadedAt,
        boolean checksumVerified,
        List<ChatMessageResponse> messages
) {
}
