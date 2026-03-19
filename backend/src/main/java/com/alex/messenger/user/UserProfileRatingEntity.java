package com.alex.messenger.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "user_profile_ratings")
public class UserProfileRatingEntity {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "rating_score", nullable = false)
    private Long ratingScore;

    @Column(name = "rating_level", nullable = false, length = 32)
    private String ratingLevel;

    @Column(name = "received_gift_count", nullable = false)
    private Long receivedGiftCount;

    @Column(name = "sent_gift_count", nullable = false)
    private Long sentGiftCount;

    @Column(name = "received_gift_premium_days", nullable = false)
    private Long receivedGiftPremiumDays;

    @Column(name = "sent_gift_premium_days", nullable = false)
    private Long sentGiftPremiumDays;

    @Column(name = "stars_received_units", nullable = false)
    private Long starsReceivedUnits;

    @Column(name = "stars_spent_units", nullable = false)
    private Long starsSpentUnits;

    @Column(name = "successful_transaction_count", nullable = false)
    private Long successfulTransactionCount;

    @Column(name = "last_recomputed_at", nullable = false)
    private Instant lastRecomputedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (ratingScore == null) {
            ratingScore = 0L;
        }
        if (ratingLevel == null) {
            ratingLevel = "NEW";
        }
        if (receivedGiftCount == null) {
            receivedGiftCount = 0L;
        }
        if (sentGiftCount == null) {
            sentGiftCount = 0L;
        }
        if (receivedGiftPremiumDays == null) {
            receivedGiftPremiumDays = 0L;
        }
        if (sentGiftPremiumDays == null) {
            sentGiftPremiumDays = 0L;
        }
        if (starsReceivedUnits == null) {
            starsReceivedUnits = 0L;
        }
        if (starsSpentUnits == null) {
            starsSpentUnits = 0L;
        }
        if (successfulTransactionCount == null) {
            successfulTransactionCount = 0L;
        }
        if (lastRecomputedAt == null) {
            lastRecomputedAt = now;
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
