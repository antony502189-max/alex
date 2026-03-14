package com.alex.messenger.monetization.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MonetizationPayoutResponse(
        UUID payoutId,
        UUID channelChatId,
        UUID recipientUserId,
        UUID triggeredByUserId,
        String triggerMode,
        String status,
        long totalUnits,
        int sponsoredMessageCount,
        Instant periodStartedAt,
        Instant periodEndedAt,
        Instant createdAt,
        Instant completedAt,
        List<MonetizationPayoutItemResponse> items
) {
}
