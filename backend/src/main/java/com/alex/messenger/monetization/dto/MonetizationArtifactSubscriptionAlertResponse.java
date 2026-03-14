package com.alex.messenger.monetization.dto;

import java.time.Instant;
import java.util.UUID;

public record MonetizationArtifactSubscriptionAlertResponse(
        UUID alertId,
        UUID subscriptionId,
        UUID channelChatId,
        UUID targetChatId,
        String severity,
        int failureCount,
        String lastFailureReason,
        String status,
        UUID publishedMessageId,
        UUID acknowledgedByUserId,
        Instant acknowledgedAt,
        Instant snoozedUntil,
        UUID ownerUserId,
        Instant assignedAt,
        Instant acknowledgeByDueAt,
        Instant resolveByDueAt,
        Instant lastReminderAt,
        int reminderCount,
        UUID lastReminderMessageId,
        UUID lastReminderTargetChatId,
        Instant severityEscalatedAt,
        Instant breachedAt,
        UUID breachMessageId,
        Instant triagedAt,
        UUID triageMessageId,
        UUID triageTargetChatId,
        Instant lastTriageReminderAt,
        int triageReminderCount,
        UUID lastTriageReminderMessageId,
        UUID lastTriageReminderTargetChatId,
        Instant triageEscalatedAt,
        UUID triageEscalationMessageId,
        UUID triageEscalationTargetChatId,
        Instant createdAt,
        Instant resolvedAt
) {
}
