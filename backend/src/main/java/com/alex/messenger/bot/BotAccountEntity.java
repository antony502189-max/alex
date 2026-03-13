package com.alex.messenger.bot;

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
@Table(name = "bot_accounts")
public class BotAccountEntity {

    @Id
    @Column(name = "bot_user_id", nullable = false, updatable = false)
    private UUID botUserId;

    @Column(name = "owner_user_id", nullable = false, updatable = false)
    private UUID ownerUserId;

    @Column(name = "api_token_hash", nullable = false, length = 64)
    private String apiTokenHash;

    @Column(name = "api_token_prefix", nullable = false, length = 24)
    private String apiTokenPrefix;

    @Column(name = "token_rotated_at", nullable = false)
    private Instant tokenRotatedAt;

    @Column(name = "webhook_url", length = 512)
    private String webhookUrl;

    @Column(name = "webhook_secret_hash", length = 64)
    private String webhookSecretHash;

    @Column(name = "webhook_secret_value", length = 255)
    private String webhookSecretValue;

    @Column(name = "webhook_enabled", nullable = false)
    private boolean webhookEnabled;

    @Column(name = "last_webhook_delivery_at")
    private Instant lastWebhookDeliveryAt;

    @Column(name = "last_webhook_error", length = 255)
    private String lastWebhookError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (tokenRotatedAt == null) {
            tokenRotatedAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
