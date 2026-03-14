package com.alex.messenger.monetization.dto;

import java.time.Instant;
import java.util.UUID;

public record MonetizationArtifactSubscriptionFailureResponse(
        UUID failureId,
        UUID subscriptionId,
        UUID channelChatId,
        UUID targetChatId,
        String artifactType,
        int attemptNumber,
        String failureReason,
        boolean alertCreated,
        Instant failedAt
) {
}
