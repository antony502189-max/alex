package com.alex.messenger.business.dto;

import java.time.Instant;
import java.util.UUID;

public record BusinessOperatorAssignmentResponse(
        UUID ownerUserId,
        UUID chatId,
        UUID operatorUserId,
        String operatorDisplayName,
        String operatorUsername,
        String note,
        Instant assignedAt,
        Instant updatedAt
) {
}
