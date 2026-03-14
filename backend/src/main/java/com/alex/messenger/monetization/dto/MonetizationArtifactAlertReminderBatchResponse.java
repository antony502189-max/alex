package com.alex.messenger.monetization.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MonetizationArtifactAlertReminderBatchResponse(
        UUID ownerUserId,
        String ownerDisplayName,
        int dueAlerts,
        int remindedAlerts,
        Instant processedAt,
        List<MonetizationArtifactAlertReminderResponse> reminders
) {
}
