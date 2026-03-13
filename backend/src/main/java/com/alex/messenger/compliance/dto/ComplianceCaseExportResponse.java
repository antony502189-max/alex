package com.alex.messenger.compliance.dto;

import com.alex.messenger.message.dto.ChatMessageResponse;
import java.time.Instant;
import java.util.List;

public record ComplianceCaseExportResponse(
        ComplianceCaseResponse caseInfo,
        String exportedByOperatorId,
        Instant exportedAt,
        int messageCount,
        String artifactChecksum,
        List<ChatMessageResponse> messages
) {
}
