package com.alex.messenger.monetization.dto;

import java.time.Instant;
import java.util.UUID;

public record MonetizationAlertPolicyResponse(
        UUID channelChatId,
        UUID configuredByUserId,
        int alertThreshold,
        int highSeverityThreshold,
        int alertSuppressionMinutes,
        int acknowledgeSlaMinutes,
        int resolveSlaMinutes,
        int reminderIntervalMinutes,
        int severityUpgradeAfterMinutes,
        int breachEscalationAfterMinutes,
        int highSeverityAcknowledgeSlaMinutes,
        int highSeverityResolveSlaMinutes,
        int highSeverityReminderIntervalMinutes,
        int triageDelayMinutes,
        int triageReminderIntervalMinutes,
        int triageEscalationAfterMinutes,
        boolean autoDigestEnabled,
        boolean autoTriageEnabled,
        boolean triageAutoAssignEnabled,
        String claimNextStrategy,
        boolean claimNextTriageOnlyDefault,
        UUID alertTargetChatId,
        UUID reminderTargetChatId,
        UUID personalReminderTargetChatId,
        UUID breachTargetChatId,
        UUID defaultOwnerUserId,
        UUID triageFallbackOwnerUserId,
        UUID triageTargetChatId,
        UUID triageEscalationTargetChatId,
        UUID digestTargetChatId,
        UUID personalReminderDigestTargetChatId,
        Instant createdAt,
        Instant updatedAt
) {
}
