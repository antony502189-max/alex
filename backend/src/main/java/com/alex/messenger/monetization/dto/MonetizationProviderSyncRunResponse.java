package com.alex.messenger.monetization.dto;

import java.time.Instant;
import java.util.UUID;

public record MonetizationProviderSyncRunResponse(
        UUID providerSyncRunId,
        UUID channelChatId,
        UUID triggeredByUserId,
        String triggerMode,
        int payloadSize,
        int appliedCount,
        int ignoredCount,
        int failedCount,
        UUID artifactId,
        Instant createdAt
) {
}
