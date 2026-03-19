package com.alex.messenger.story;

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
@Table(name = "story_live_comments")
public class StoryLiveCommentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "story_id", nullable = false)
    private UUID storyId;

    @Column(name = "live_session_id", nullable = false)
    private UUID liveSessionId;

    @Column(name = "author_user_id", nullable = false)
    private UUID authorUserId;

    @Column(name = "message_text", length = 500)
    private String messageText;

    @Column(name = "donation_amount_minor")
    private Long donationAmountMinor;

    @Column(name = "donation_currency", length = 8)
    private String donationCurrency;

    @Column(name = "hook_delivery_attempts", nullable = false)
    private Integer hookDeliveryAttempts;

    @Column(name = "last_hook_delivery_attempt_at")
    private Instant lastHookDeliveryAttemptAt;

    @Column(name = "hook_delivered_at")
    private Instant hookDeliveredAt;

    @Column(name = "last_hook_error", length = 255)
    private String lastHookError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (hookDeliveryAttempts == null) {
            hookDeliveryAttempts = 0;
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
