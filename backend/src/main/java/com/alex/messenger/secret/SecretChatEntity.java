package com.alex.messenger.secret;

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
@Table(name = "secret_chats")
public class SecretChatEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "initiator_user_id", nullable = false)
    private UUID initiatorUserId;

    @Column(name = "initiator_session_id", nullable = false)
    private UUID initiatorSessionId;

    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    @Column(name = "recipient_session_id")
    private UUID recipientSessionId;

    @Column(name = "initiator_public_key", nullable = false, length = 255)
    private String initiatorPublicKey;

    @Column(name = "recipient_public_key", length = 255)
    private String recipientPublicKey;

    @Column(name = "shared_key_fingerprint", length = 128)
    private String sharedKeyFingerprint;

    @Column(name = "auto_delete_seconds")
    private Integer autoDeleteSeconds;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @PrePersist
    void prePersist() {
        if (status == null) {
            status = "PENDING";
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
