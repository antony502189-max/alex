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
@Table(name = "channel_monetization_artifact_subscription_failures")
public class ChannelMonetizationArtifactSubscriptionFailureEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Column(name = "channel_chat_id", nullable = false)
    private UUID channelChatId;

    @Column(name = "target_chat_id", nullable = false)
    private UUID targetChatId;

    @Column(name = "artifact_type", nullable = false, length = 32)
    private String artifactType;

    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber = 1;

    @Column(name = "failure_reason", nullable = false, length = 255)
    private String failureReason;

    @Column(name = "alert_created", nullable = false)
    private boolean alertCreated;

    @Column(name = "failed_at", nullable = false, updatable = false)
    private Instant failedAt;

    @PrePersist
    void prePersist() {
        if (attemptNumber == null) {
            attemptNumber = 1;
        }
        if (failedAt == null) {
            failedAt = Instant.now();
        }
    }
}
