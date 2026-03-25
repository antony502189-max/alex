package com.alex.messenger.sticker;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "user_recent_stickers")
public class UserRecentStickerEntity {

    @EmbeddedId
    private UserStickerId id;

    @Column(name = "used_at", nullable = false)
    private Instant usedAt;

    @Column(name = "usage_count", nullable = false)
    private Integer usageCount;

    @PrePersist
    void prePersist() {
        if (usedAt == null) {
            usedAt = Instant.now();
        }
        if (usageCount == null) {
            usageCount = 1;
        }
    }
}
