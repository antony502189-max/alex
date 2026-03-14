package com.alex.messenger.monetization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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
@Table(name = "channel_monetization_alert_policies")
public class ChannelMonetizationAlertPolicyEntity {

    @Id
    @Column(name = "channel_chat_id", nullable = false)
    private UUID channelChatId;

    @Column(name = "configured_by_user_id")
    private UUID configuredByUserId;

    @Column(name = "alert_threshold", nullable = false)
    private Integer alertThreshold = 3;

    @Column(name = "high_severity_threshold", nullable = false)
    private Integer highSeverityThreshold = 5;

    @Column(name = "alert_suppression_minutes", nullable = false)
    private Integer alertSuppressionMinutes = 180;

    @Column(name = "acknowledge_sla_minutes", nullable = false)
    private Integer acknowledgeSlaMinutes = 60;

    @Column(name = "resolve_sla_minutes", nullable = false)
    private Integer resolveSlaMinutes = 240;

    @Column(name = "reminder_interval_minutes", nullable = false)
    private Integer reminderIntervalMinutes = 60;

    @Column(name = "severity_upgrade_after_minutes", nullable = false)
    private Integer severityUpgradeAfterMinutes = 30;

    @Column(name = "breach_escalation_after_minutes", nullable = false)
    private Integer breachEscalationAfterMinutes = 120;

    @Column(name = "high_severity_acknowledge_sla_minutes", nullable = false)
    private Integer highSeverityAcknowledgeSlaMinutes = 15;

    @Column(name = "high_severity_resolve_sla_minutes", nullable = false)
    private Integer highSeverityResolveSlaMinutes = 60;

    @Column(name = "high_severity_reminder_interval_minutes", nullable = false)
    private Integer highSeverityReminderIntervalMinutes = 15;

    @Column(name = "triage_delay_minutes", nullable = false)
    private Integer triageDelayMinutes = 15;

    @Column(name = "triage_reminder_interval_minutes", nullable = false)
    private Integer triageReminderIntervalMinutes = 30;

    @Column(name = "triage_escalation_after_minutes", nullable = false)
    private Integer triageEscalationAfterMinutes = 90;

    @Column(name = "auto_digest_enabled", nullable = false)
    private boolean autoDigestEnabled = true;

    @Column(name = "auto_triage_enabled", nullable = false)
    private boolean autoTriageEnabled;

    @Column(name = "triage_auto_assign_enabled", nullable = false)
    private boolean triageAutoAssignEnabled;

    @Column(name = "claim_next_strategy", nullable = false)
    private String claimNextStrategy = "DEFAULT";

    @Column(name = "claim_next_triage_only_default", nullable = false)
    private boolean claimNextTriageOnlyDefault;

    @Column(name = "alert_target_chat_id")
    private UUID alertTargetChatId;

    @Column(name = "reminder_target_chat_id")
    private UUID reminderTargetChatId;

    @Column(name = "personal_reminder_target_chat_id")
    private UUID personalReminderTargetChatId;

    @Column(name = "breach_target_chat_id")
    private UUID breachTargetChatId;

    @Column(name = "default_owner_user_id")
    private UUID defaultOwnerUserId;

    @Column(name = "triage_fallback_owner_user_id")
    private UUID triageFallbackOwnerUserId;

    @Column(name = "triage_target_chat_id")
    private UUID triageTargetChatId;

    @Column(name = "triage_escalation_target_chat_id")
    private UUID triageEscalationTargetChatId;

    @Column(name = "digest_target_chat_id")
    private UUID digestTargetChatId;

    @Column(name = "personal_reminder_digest_target_chat_id")
    private UUID personalReminderDigestTargetChatId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (alertThreshold == null) {
            alertThreshold = 3;
        }
        if (highSeverityThreshold == null) {
            highSeverityThreshold = 5;
        }
        if (alertSuppressionMinutes == null) {
            alertSuppressionMinutes = 180;
        }
        if (acknowledgeSlaMinutes == null) {
            acknowledgeSlaMinutes = 60;
        }
        if (resolveSlaMinutes == null) {
            resolveSlaMinutes = 240;
        }
        if (reminderIntervalMinutes == null) {
            reminderIntervalMinutes = 60;
        }
        if (severityUpgradeAfterMinutes == null) {
            severityUpgradeAfterMinutes = 30;
        }
        if (breachEscalationAfterMinutes == null) {
            breachEscalationAfterMinutes = 120;
        }
        if (highSeverityAcknowledgeSlaMinutes == null) {
            highSeverityAcknowledgeSlaMinutes = 15;
        }
        if (highSeverityResolveSlaMinutes == null) {
            highSeverityResolveSlaMinutes = 60;
        }
        if (highSeverityReminderIntervalMinutes == null) {
            highSeverityReminderIntervalMinutes = 15;
        }
        if (triageDelayMinutes == null) {
            triageDelayMinutes = 15;
        }
        if (triageReminderIntervalMinutes == null) {
            triageReminderIntervalMinutes = 30;
        }
        if (triageEscalationAfterMinutes == null) {
            triageEscalationAfterMinutes = 90;
        }
        if (claimNextStrategy == null || claimNextStrategy.isBlank()) {
            claimNextStrategy = "DEFAULT";
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
