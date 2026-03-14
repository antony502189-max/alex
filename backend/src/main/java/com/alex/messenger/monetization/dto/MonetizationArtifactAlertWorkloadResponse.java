package com.alex.messenger.monetization.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MonetizationArtifactAlertWorkloadResponse(
        UUID channelChatId,
        int totalAlerts,
        int openAlerts,
        int highSeverityOpenAlerts,
        int breachedAlerts,
        int overdueAlerts,
        int unassignedAlerts,
        int unassignedHighSeverityAlerts,
        int assignedOwnerCount,
        Instant latestAlertAt,
        List<MonetizationArtifactAlertWorkloadOwnerResponse> owners
) {
}
