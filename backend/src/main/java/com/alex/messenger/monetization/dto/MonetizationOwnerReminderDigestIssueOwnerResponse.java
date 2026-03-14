package com.alex.messenger.monetization.dto;

import java.time.Instant;
import java.util.UUID;

public record MonetizationOwnerReminderDigestIssueOwnerResponse(
        UUID ownerUserId,
        String ownerDisplayName,
        int totalIssues,
        int backoffSubscriptions,
        int autoPausedSubscriptions,
        int dueRetrySubscriptions,
        Instant latestFailureAt
) {
}
