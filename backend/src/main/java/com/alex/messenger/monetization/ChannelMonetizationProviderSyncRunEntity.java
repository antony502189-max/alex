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
@Table(name = "channel_monetization_provider_sync_runs")
public class ChannelMonetizationProviderSyncRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "channel_chat_id", nullable = false)
    private UUID channelChatId;

    @Column(name = "triggered_by_user_id")
    private UUID triggeredByUserId;

    @Column(name = "trigger_mode", nullable = false, length = 16)
    private String triggerMode;

    @Column(name = "payload_size", nullable = false)
    private Integer payloadSize = 0;

    @Column(name = "applied_count", nullable = false)
    private Integer appliedCount = 0;

    @Column(name = "ignored_count", nullable = false)
    private Integer ignoredCount = 0;

    @Column(name = "failed_count", nullable = false)
    private Integer failedCount = 0;

    @Column(name = "artifact_id")
    private UUID artifactId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (payloadSize == null) {
            payloadSize = 0;
        }
        if (appliedCount == null) {
            appliedCount = 0;
        }
        if (ignoredCount == null) {
            ignoredCount = 0;
        }
        if (failedCount == null) {
            failedCount = 0;
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
