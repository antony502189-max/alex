package com.alex.messenger.message;

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
@Table(name = "message_live_locations")
public class MessageLiveLocationEntity {

    @Id
    @Column(name = "message_id", nullable = false, updatable = false)
    private UUID messageId;

    @Column(name = "chat_id", nullable = false)
    private UUID chatId;

    @Column(name = "sender_user_id", nullable = false)
    private UUID senderUserId;

    @Column(name = "latitude", nullable = false)
    private Double latitude;

    @Column(name = "longitude", nullable = false)
    private Double longitude;

    @Column(name = "title", length = 120)
    private String title;

    @Column(name = "address", length = 240)
    private String address;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "last_updated_at", nullable = false)
    private Instant lastUpdatedAt;

    @Column(name = "stopped_at")
    private Instant stoppedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (lastUpdatedAt == null) {
            lastUpdatedAt = now;
        }
    }
}
