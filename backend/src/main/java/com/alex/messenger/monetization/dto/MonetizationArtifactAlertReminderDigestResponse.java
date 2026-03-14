package com.alex.messenger.monetization.dto;

import java.time.Instant;
import java.util.UUID;

public record MonetizationArtifactAlertReminderDigestResponse(
        UUID ownerUserId,
        String ownerDisplayName,
        int dueAlerts,
        int highSeverityDueAlerts,
        int breachedDueAlerts,
        int overdueDueAlerts,
        UUID nextAlertId,
        UUID nextSubscriptionId,
        String nextSeverity,
        Instant latestAlertAt
) {
}
