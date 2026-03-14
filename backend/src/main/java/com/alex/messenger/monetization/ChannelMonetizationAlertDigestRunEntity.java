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
@Table(name = "channel_monetization_alert_digest_runs")
public class ChannelMonetizationAlertDigestRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "channel_chat_id", nullable = false)
    private UUID channelChatId;

    @Column(name = "generated_by_user_id")
    private UUID generatedByUserId;

    @Column(name = "trigger_mode", nullable = false, length = 16)
    private String triggerMode;

    @Column(name = "open_alert_count", nullable = false)
    private Integer openAlertCount = 0;

    @Column(name = "affected_subscription_count", nullable = false)
    private Integer affectedSubscriptionCount = 0;

    @Column(name = "artifact_id")
    private UUID artifactId;

    @Column(name = "published_message_id")
    private UUID publishedMessageId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (openAlertCount == null) {
            openAlertCount = 0;
        }
        if (affectedSubscriptionCount == null) {
            affectedSubscriptionCount = 0;
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
