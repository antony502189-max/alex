package com.alex.messenger.premium;

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
@Table(name = "premium_gifts")
public class PremiumGiftEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "sender_user_id", nullable = false)
    private UUID senderUserId;

    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    @Column(name = "custom_emoji_id")
    private UUID customEmojiId;

    @Column(name = "message", length = 255)
    private String message;

    @Column(name = "premium_days_granted", nullable = false)
    private Integer premiumDaysGranted = 30;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (premiumDaysGranted == null) {
            premiumDaysGranted = 30;
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
