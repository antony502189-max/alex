package com.alex.messenger.monetization.dto;

import java.time.Instant;
import java.util.UUID;

public record MonetizationOwnerReminderDigestRunResponse(
        UUID runId,
        UUID subscriptionId,
        UUID channelChatId,
        UUID ownerUserId,
        UUID processedByUserId,
        String triggerMode,
        String status,
        UUID targetChatId,
        String severity,
        boolean breachedOnly,
        int dueAlertCount,
        int breachedDueAlertCount,
        UUID artifactId,
        UUID publicationId,
        UUID publishedMessageId,
        String failureReason,
        Instant processedAt
) {
}
