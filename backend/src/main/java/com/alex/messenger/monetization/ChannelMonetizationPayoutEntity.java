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
@Table(name = "channel_monetization_payouts")
public class ChannelMonetizationPayoutEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "channel_chat_id", nullable = false)
    private UUID channelChatId;

    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    @Column(name = "triggered_by_user_id")
    private UUID triggeredByUserId;

    @Column(name = "trigger_mode", nullable = false, length = 16)
    private String triggerMode;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "COMPLETED";

    @Column(name = "total_units", nullable = false)
    private Long totalUnits = 0L;

    @Column(name = "sponsored_message_count", nullable = false)
    private Integer sponsoredMessageCount = 0;

    @Column(name = "period_started_at")
    private Instant periodStartedAt;

    @Column(name = "period_ended_at")
    private Instant periodEndedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (status == null) {
            status = "COMPLETED";
        }
        if (totalUnits == null) {
            totalUnits = 0L;
        }
        if (sponsoredMessageCount == null) {
            sponsoredMessageCount = 0;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        if (completedAt == null && "COMPLETED".equals(status)) {
            completedAt = now;
        }
    }
}
