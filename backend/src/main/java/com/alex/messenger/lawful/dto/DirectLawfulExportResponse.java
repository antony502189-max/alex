package com.alex.messenger.lawful.dto;

import com.alex.messenger.message.dto.ChatMessageResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DirectLawfulExportResponse(
        UUID exportId,
        UUID targetUserId,
        String operatorId,
        String reason,
        Instant fromInclusive,
        Instant toExclusive,
        boolean includeAttachmentsMetadata,
        Instant exportedAt,
        int messageCount,
        String artifactChecksum,
        String artifactLocation,
        List<ChatMessageResponse> messages
) {
}
