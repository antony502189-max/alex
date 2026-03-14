package com.alex.messenger.monetization.dto;

import java.util.UUID;

public record MonetizationClaimableAlertWorkloadResponse(
        UUID channelChatId,
        int totalClaimableAlerts,
        int highSeverityClaimableAlerts,
        int breachedClaimableAlerts,
        int overdueClaimableAlerts,
        int triageClaimableAlerts,
        int triageFollowUpClaimableAlerts,
        UUID nextAlertId,
        UUID nextSubscriptionId,
        String nextSeverity,
        String nextStatus
) {
}
