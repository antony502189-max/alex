package com.alex.messenger.call;

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
@Table(name = "call_join_links")
public class CallJoinLinkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "chat_id", nullable = false)
    private UUID chatId;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(name = "kind", nullable = false, length = 16)
    private String kind;

    @Column(name = "mode", nullable = false, length = 32)
    private String mode;

    @Column(name = "label", length = 120)
    private String label;

    @Column(name = "token", nullable = false, unique = true, length = 64)
    private String token;

    @Column(name = "revoked", nullable = false)
    private Boolean revoked = false;

    @Column(name = "usage_count", nullable = false)
    private Integer usageCount = 0;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @PrePersist
    void prePersist() {
        if (mode == null) {
            mode = "GROUP";
        }
        if (revoked == null) {
            revoked = false;
        }
        if (usageCount == null) {
            usageCount = 0;
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
