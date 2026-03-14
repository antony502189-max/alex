package com.alex.messenger.monetization.dto;

import java.time.Instant;
import java.util.UUID;

public record MonetizationArtifactSubscriptionResponse(
        UUID subscriptionId,
        UUID channelChatId,
        UUID targetChatId,
        UUID createdByUserId,
        String artifactType,
        String deliveryMode,
        String note,
        String status,
        int minIntervalMinutes,
        boolean autoGenerate,
        UUID lastDeliveredArtifactId,
        Instant lastDeliveredAt,
        Instant lastGeneratedAt,
        int consecutiveFailureCount,
        Instant lastFailureAt,
        String lastFailureReason,
        String escalationStatus,
        int alertSuppressionMinutes,
        Instant lastAlertedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
