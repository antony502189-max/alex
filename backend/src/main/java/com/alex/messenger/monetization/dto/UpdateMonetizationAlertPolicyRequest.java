package com.alex.messenger.monetization.dto;

import java.util.UUID;

public record UpdateMonetizationAlertPolicyRequest(
        Integer alertThreshold,
        Integer highSeverityThreshold,
        Integer alertSuppressionMinutes,
        Integer acknowledgeSlaMinutes,
        Integer resolveSlaMinutes,
        Integer reminderIntervalMinutes,
        Integer severityUpgradeAfterMinutes,
        Integer breachEscalationAfterMinutes,
        Integer highSeverityAcknowledgeSlaMinutes,
        Integer highSeverityResolveSlaMinutes,
        Integer highSeverityReminderIntervalMinutes,
        Integer triageDelayMinutes,
        Integer triageReminderIntervalMinutes,
        Integer triageEscalationAfterMinutes,
        Boolean autoDigestEnabled,
        Boolean autoTriageEnabled,
        Boolean triageAutoAssignEnabled,
        String claimNextStrategy,
        Boolean claimNextTriageOnlyDefault,
        UUID alertTargetChatId,
        UUID reminderTargetChatId,
        UUID personalReminderTargetChatId,
        UUID breachTargetChatId,
        UUID defaultOwnerUserId,
        UUID triageFallbackOwnerUserId,
        UUID triageTargetChatId,
        UUID triageEscalationTargetChatId,
        UUID digestTargetChatId,
        UUID personalReminderDigestTargetChatId
) {
}
