package com.alex.messenger.premium;

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
@Table(name = "premium_entitlements")
public class PremiumEntitlementEntity {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "tier", nullable = false, length = 32)
    private String tier;

    @Column(name = "active_until")
    private Instant activeUntil;

    @Column(name = "custom_emoji_status_id")
    private UUID customEmojiStatusId;

    @Column(name = "custom_emoji_status_emoji", length = 16)
    private String customEmojiStatusEmoji;

    @Column(name = "custom_emoji_status_label", length = 64)
    private String customEmojiStatusLabel;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (tier == null) {
            tier = "PREMIUM";
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
