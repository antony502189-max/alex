package com.alex.messenger.sticker;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "stickers")
public class StickerEntity {

    @Id
    private UUID id;

    @Column(name = "pack_id", nullable = false)
    private UUID packId;

    @Column(name = "emoji", nullable = false, length = 16)
    private String emoji;

    @Column(name = "label", nullable = false, length = 64)
    private String label;

    @Column(name = "background_from", nullable = false, length = 16)
    private String backgroundFrom;

    @Column(name = "background_to", nullable = false, length = 16)
    private String backgroundTo;

    @Column(name = "text_color", nullable = false, length = 16)
    private String textColor;

    @Column(name = "position", nullable = false)
    private Integer position;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
