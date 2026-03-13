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
@Table(name = "bot_updates")
public class BotUpdateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bot_user_id", nullable = false, updatable = false)
    private UUID botUserId;

    @Column(name = "chat_id", nullable = false, updatable = false)
    private UUID chatId;

    @Column(name = "message_id", updatable = false)
    private UUID messageId;

    @Column(name = "callback_query_id", updatable = false)
    private UUID callbackQueryId;

    @Column(name = "web_app_event_id", updatable = false)
    private UUID webAppEventId;

    @Column(name = "web_app_query_id", updatable = false)
    private UUID webAppQueryId;

    @Column(name = "pre_checkout_query_id", updatable = false)
    private UUID preCheckoutQueryId;

    @Column(name = "update_type", nullable = false, length = 32, updatable = false)
    private String updateType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "last_delivery_attempt_at")
    private Instant lastDeliveryAttemptAt;

    @Column(name = "delivery_attempts", nullable = false)
    private int deliveryAttempts;

    @Column(name = "last_error", length = 255)
    private String lastError;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
