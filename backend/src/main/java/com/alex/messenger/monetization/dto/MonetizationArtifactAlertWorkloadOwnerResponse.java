package com.alex.messenger.monetization.dto;

import java.time.Instant;
import java.util.UUID;

public record MonetizationArtifactAlertWorkloadOwnerResponse(
        UUID ownerUserId,
        String ownerDisplayName,
        int totalAlerts,
        int openAlerts,
        int acknowledgedAlerts,
        int snoozedAlerts,
        int highSeverityAlerts,
        int breachedAlerts,
        int overdueAlerts,
        Instant latestAssignedAt,
        Instant latestAlertAt
) {
}
