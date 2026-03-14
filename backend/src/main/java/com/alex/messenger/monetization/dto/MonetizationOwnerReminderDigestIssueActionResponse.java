package com.alex.messenger.monetization.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MonetizationOwnerReminderDigestIssueActionResponse(
        UUID channelChatId,
        UUID ownerUserId,
        String failureState,
        boolean retryDueOnly,
        int matchedSubscriptions,
        int processedSubscriptions,
        Instant processedAt,
        List<UUID> subscriptionIds,
        List<UUID> runIds
) {
}
