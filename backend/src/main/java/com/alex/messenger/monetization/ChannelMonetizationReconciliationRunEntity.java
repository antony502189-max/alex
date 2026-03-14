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
@Table(name = "channel_monetization_reconciliation_runs")
public class ChannelMonetizationReconciliationRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "channel_chat_id", nullable = false)
    private UUID channelChatId;

    @Column(name = "triggered_by_user_id")
    private UUID triggeredByUserId;

    @Column(name = "trigger_mode", nullable = false, length = 16)
    private String triggerMode;

    @Column(name = "processed_count", nullable = false)
    private Integer processedCount = 0;

    @Column(name = "pending_count", nullable = false)
    private Integer pendingCount = 0;

    @Column(name = "processing_count", nullable = false)
    private Integer processingCount = 0;

    @Column(name = "completed_count", nullable = false)
    private Integer completedCount = 0;

    @Column(name = "failed_count", nullable = false)
    private Integer failedCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (processedCount == null) {
            processedCount = 0;
        }
        if (pendingCount == null) {
            pendingCount = 0;
        }
        if (processingCount == null) {
            processingCount = 0;
        }
        if (completedCount == null) {
            completedCount = 0;
        }
        if (failedCount == null) {
            failedCount = 0;
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
