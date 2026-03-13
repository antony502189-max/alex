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
@Table(name = "sponsored_messages")
public class SponsoredMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "channel_chat_id", nullable = false)
    private UUID channelChatId;

    @Column(name = "sponsor_user_id", nullable = false)
    private UUID sponsorUserId;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Column(name = "message_text", nullable = false, length = 1000)
    private String messageText;

    @Column(name = "call_to_action_label", length = 64)
    private String callToActionLabel;

    @Column(name = "call_to_action_url", length = 512)
    private String callToActionUrl;

    @Column(name = "budget_units", nullable = false)
    private Long budgetUnits;

    @Column(name = "spent_units", nullable = false)
    private Long spentUnits = 0L;

    @Column(name = "cost_per_impression_units", nullable = false)
    private Long costPerImpressionUnits = 1L;

    @Column(name = "cost_per_click_units", nullable = false)
    private Long costPerClickUnits = 5L;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "DRAFT";

    @Column(name = "delivered_message_id")
    private UUID deliveredMessageId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "active_until")
    private Instant activeUntil;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (spentUnits == null) {
            spentUnits = 0L;
        }
        if (costPerImpressionUnits == null) {
            costPerImpressionUnits = 1L;
        }
        if (costPerClickUnits == null) {
            costPerClickUnits = 5L;
        }
        if (status == null) {
            status = "DRAFT";
        }
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
