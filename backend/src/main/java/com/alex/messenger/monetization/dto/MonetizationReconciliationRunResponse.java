package com.alex.messenger.monetization.dto;

import java.time.Instant;
import java.util.UUID;

public record MonetizationReconciliationRunResponse(
        UUID reconciliationRunId,
        UUID channelChatId,
        UUID triggeredByUserId,
        String triggerMode,
        int processedCount,
        int pendingCount,
        int processingCount,
        int completedCount,
        int failedCount,
        Instant createdAt
) {
}
