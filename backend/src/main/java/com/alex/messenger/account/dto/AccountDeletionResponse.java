package com.alex.messenger.account.dto;

import java.time.Instant;
import java.util.UUID;

public record AccountDeletionResponse(
        UUID jobId,
        String triggerType,
        String status,
        String reason,
        Instant scheduledFor,
        Instant createdAt,
        Instant executedAt
) {
}
