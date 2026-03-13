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
@Table(name = "premium_custom_emojis")
public class PremiumCustomEmojiEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "short_code", nullable = false, unique = true, length = 64)
    private String shortCode;

    @Column(name = "emoji", nullable = false, length = 16)
    private String emoji;

    @Column(name = "label", nullable = false, length = 64)
    private String label;

    @Column(name = "premium_required", nullable = false)
    private Boolean premiumRequired = true;

    @Column(name = "position", nullable = false)
    private Integer position = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (premiumRequired == null) {
            premiumRequired = true;
        }
        if (position == null) {
            position = 0;
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
