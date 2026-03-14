package com.alex.messenger.monetization.dto;

import java.time.Instant;
import java.util.UUID;

public record MonetizationArtifactAlertSummaryResponse(
        UUID channelChatId,
        int totalAlerts,
        int openAlerts,
        int acknowledgedAlerts,
        int snoozedAlerts,
        int resolvedAlerts,
        int highSeverityOpenAlerts,
        int warnSeverityOpenAlerts,
        int overdueAcknowledgementAlerts,
        int overdueResolutionAlerts,
        int breachedAlerts,
        int openSubscriptions,
        int suppressedSubscriptions,
        int acknowledgedSubscriptions,
        int snoozedSubscriptions,
        Instant latestAlertAt,
        Instant latestFailureAt,
        UUID latestDigestRunId,
        Instant latestDigestAt
) {
}
