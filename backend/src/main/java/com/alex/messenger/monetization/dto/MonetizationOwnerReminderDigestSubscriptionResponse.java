package com.alex.messenger.monetization.dto;

import java.time.Instant;
import java.util.UUID;

public record MonetizationOwnerReminderDigestSubscriptionResponse(
        UUID subscriptionId,
        UUID channelChatId,
        UUID ownerUserId,
        UUID targetChatId,
        UUID createdByUserId,
        String severity,
        boolean breachedOnly,
        String note,
        String status,
        int minIntervalMinutes,
        UUID lastDeliveredArtifactId,
        Instant lastDeliveredAt,
        Instant lastProcessedAt,
        int consecutiveFailureCount,
        String failureState,
        Instant lastFailureAt,
        String lastFailureReason,
        Instant nextRetryAt,
        Instant autoPausedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
