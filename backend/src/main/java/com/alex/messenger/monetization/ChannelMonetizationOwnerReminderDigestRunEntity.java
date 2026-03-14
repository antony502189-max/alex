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
@Table(name = "channel_monetization_owner_reminder_digest_runs")
public class ChannelMonetizationOwnerReminderDigestRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Column(name = "channel_chat_id", nullable = false)
    private UUID channelChatId;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(name = "processed_by_user_id")
    private UUID processedByUserId;

    @Column(name = "trigger_mode", nullable = false, length = 16)
    private String triggerMode;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "target_chat_id")
    private UUID targetChatId;

    @Column(name = "severity", length = 16)
    private String severity;

    @Column(name = "breached_only", nullable = false)
    private boolean breachedOnly;

    @Column(name = "due_alert_count", nullable = false)
    private Integer dueAlertCount = 0;

    @Column(name = "breached_due_alert_count", nullable = false)
    private Integer breachedDueAlertCount = 0;

    @Column(name = "artifact_id")
    private UUID artifactId;

    @Column(name = "publication_id")
    private UUID publicationId;

    @Column(name = "published_message_id")
    private UUID publishedMessageId;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    @PrePersist
    void prePersist() {
        if (dueAlertCount == null) {
            dueAlertCount = 0;
        }
        if (breachedDueAlertCount == null) {
            breachedDueAlertCount = 0;
        }
        if (processedAt == null) {
            processedAt = Instant.now();
        }
    }
}
