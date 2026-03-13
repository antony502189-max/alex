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
@Table(name = "bot_message_actions")
public class BotMessageActionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "bot_user_id", nullable = false)
    private UUID botUserId;

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "action_type", nullable = false, length = 16)
    private String actionType;

    @Column(name = "button_text", nullable = false, length = 64)
    private String buttonText;

    @Column(name = "callback_data", length = 255)
    private String callbackData;

    @Column(name = "target_url", length = 512)
    private String targetUrl;

    @Column(name = "web_app_start_parameter", length = 128)
    private String webAppStartParameter;

    @Column(name = "payment_invoice_id")
    private UUID paymentInvoiceId;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (sortOrder == null) {
            sortOrder = 0;
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
