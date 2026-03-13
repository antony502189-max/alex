package com.alex.messenger.bot;

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
@Table(name = "bot_inline_result_cache")
public class BotInlineResultCacheEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "bot_user_id", nullable = false)
    private UUID botUserId;

    @Column(name = "query_text", nullable = false, length = 512)
    private String queryText;

    @Column(name = "result_id", nullable = false, length = 64)
    private String resultId;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "text", nullable = false, length = 4000)
    private String text;

    @Column(name = "cached_until", nullable = false)
    private Instant cachedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
