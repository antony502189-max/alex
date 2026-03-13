package com.alex.messenger.chat;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
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
@Table(name = "chat_bans")
public class ChatBanEntity {

    @EmbeddedId
    private ChatBanId id;

    @Column(name = "banned_until")
    private Instant bannedUntil;

    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "banned_by_user_id")
    private UUID bannedByUserId;

    @Column(name = "banned_at", nullable = false, updatable = false)
    private Instant bannedAt;

    @PrePersist
    void prePersist() {
        if (bannedAt == null) {
            bannedAt = Instant.now();
        }
    }
}
