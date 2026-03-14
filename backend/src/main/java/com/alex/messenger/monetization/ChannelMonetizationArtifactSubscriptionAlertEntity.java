package com.alex.messenger.monetization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "channel_monetization_artifact_subscription_alerts")
public class ChannelMonetizationArtifactSubscriptionAlertEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Column(name = "channel_chat_id", nullable = false)
    private UUID channelChatId;

    @Column(name = "target_chat_id", nullable = false)
    private UUID targetChatId;

    @Column(name = "severity", nullable = false, length = 16)
    private String severity;

    @Column(name = "failure_count", nullable = false)
    private Integer failureCount = 0;

    @Column(name = "last_failure_reason", length = 255)
    private String lastFailureReason;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "OPEN";

    @Column(name = "published_message_id")
    private UUID publishedMessageId;

    @Column(name = "acknowledged_by_user_id")
    private UUID acknowledgedByUserId;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "snoozed_until")
    private Instant snoozedUntil;

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "acknowledge_by_due_at")
    private Instant acknowledgeByDueAt;

    @Column(name = "resolve_by_due_at")
    private Instant resolveByDueAt;

    @Column(name = "last_reminder_at")
    private Instant lastReminderAt;

    @Column(name = "reminder_count", nullable = false)
    private Integer reminderCount = 0;

    @Column(name = "last_reminder_message_id")
    private UUID lastReminderMessageId;

    @Column(name = "last_reminder_target_chat_id")
    private UUID lastReminderTargetChatId;

    @Column(name = "severity_escalated_at")
    private Instant severityEscalatedAt;

    @Column(name = "breached_at")
    private Instant breachedAt;

    @Column(name = "breach_message_id")
    private UUID breachMessageId;

    @Column(name = "triaged_at")
    private Instant triagedAt;

    @Column(name = "triage_message_id")
    private UUID triageMessageId;

    @Column(name = "triage_target_chat_id")
    private UUID triageTargetChatId;

    @Column(name = "last_triage_reminder_at")
    private Instant lastTriageReminderAt;

    @Column(name = "triage_reminder_count", nullable = false)
    private Integer triageReminderCount = 0;

    @Column(name = "last_triage_reminder_message_id")
    private UUID lastTriageReminderMessageId;

    @Column(name = "last_triage_reminder_target_chat_id")
    private UUID lastTriageReminderTargetChatId;

    @Column(name = "triage_escalated_at")
    private Instant triageEscalatedAt;

    @Column(name = "triage_escalation_message_id")
    private UUID triageEscalationMessageId;

    @Column(name = "triage_escalation_target_chat_id")
    private UUID triageEscalationTargetChatId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @PrePersist
    void prePersist() {
        if (failureCount == null) {
            failureCount = 0;
        }
        if (status == null) {
            status = "OPEN";
        }
        if (reminderCount == null) {
            reminderCount = 0;
        }
        if (triageReminderCount == null) {
            triageReminderCount = 0;
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
