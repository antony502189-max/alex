package com.alex.messenger.monetization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
@Table(name = "channel_monetization_owner_reminder_digest_subscriptions")
public class ChannelMonetizationOwnerReminderDigestSubscriptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "channel_chat_id", nullable = false)
    private UUID channelChatId;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(name = "target_chat_id")
    private UUID targetChatId;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(name = "severity", length = 16)
    private String severity;

    @Column(name = "breached_only", nullable = false)
    private boolean breachedOnly;

    @Column(name = "note", length = 255)
    private String note;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "ACTIVE";

    @Column(name = "min_interval_minutes", nullable = false)
    private Integer minIntervalMinutes = 60;

    @Column(name = "last_delivered_artifact_id")
    private UUID lastDeliveredArtifactId;

    @Column(name = "last_delivered_at")
    private Instant lastDeliveredAt;

    @Column(name = "last_processed_at")
    private Instant lastProcessedAt;

    @Column(name = "consecutive_failure_count", nullable = false)
    private Integer consecutiveFailureCount = 0;

    @Column(name = "failure_state", nullable = false, length = 16)
    private String failureState = "NONE";

    @Column(name = "last_failure_at")
    private Instant lastFailureAt;

    @Column(name = "last_failure_reason", length = 255)
    private String lastFailureReason;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "auto_paused_at")
    private Instant autoPausedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (status == null) {
            status = "ACTIVE";
        }
        if (minIntervalMinutes == null) {
            minIntervalMinutes = 60;
        }
        if (consecutiveFailureCount == null) {
            consecutiveFailureCount = 0;
        }
        if (failureState == null) {
            failureState = "NONE";
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
