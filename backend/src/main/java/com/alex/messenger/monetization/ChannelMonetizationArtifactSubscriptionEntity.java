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
@Table(name = "channel_monetization_artifact_subscriptions")
public class ChannelMonetizationArtifactSubscriptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "channel_chat_id", nullable = false)
    private UUID channelChatId;

    @Column(name = "target_chat_id", nullable = false)
    private UUID targetChatId;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(name = "artifact_type", nullable = false, length = 32)
    private String artifactType;

    @Column(name = "delivery_mode", nullable = false, length = 32)
    private String deliveryMode = "CHAT_MESSAGE";

    @Column(name = "note", length = 255)
    private String note;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "ACTIVE";

    @Column(name = "min_interval_minutes", nullable = false)
    private Integer minIntervalMinutes = 60;

    @Column(name = "auto_generate", nullable = false)
    private boolean autoGenerate;

    @Column(name = "last_delivered_artifact_id")
    private UUID lastDeliveredArtifactId;

    @Column(name = "last_delivered_at")
    private Instant lastDeliveredAt;

    @Column(name = "last_generated_at")
    private Instant lastGeneratedAt;

    @Column(name = "consecutive_failure_count", nullable = false)
    private Integer consecutiveFailureCount = 0;

    @Column(name = "last_failure_at")
    private Instant lastFailureAt;

    @Column(name = "last_failure_reason", length = 255)
    private String lastFailureReason;

    @Column(name = "escalation_status", nullable = false, length = 16)
    private String escalationStatus = "NONE";

    @Column(name = "alert_suppression_minutes", nullable = false)
    private Integer alertSuppressionMinutes = 180;

    @Column(name = "last_alerted_at")
    private Instant lastAlertedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (deliveryMode == null) {
            deliveryMode = "CHAT_MESSAGE";
        }
        if (status == null) {
            status = "ACTIVE";
        }
        if (minIntervalMinutes == null) {
            minIntervalMinutes = 60;
        }
        if (consecutiveFailureCount == null) {
            consecutiveFailureCount = 0;
        }
        if (escalationStatus == null) {
            escalationStatus = "NONE";
        }
        if (alertSuppressionMinutes == null) {
            alertSuppressionMinutes = 180;
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
