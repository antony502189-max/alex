package com.alex.messenger.bot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
@Table(name = "bot_pre_checkout_queries")
public class BotPreCheckoutQueryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "bot_user_id", nullable = false)
    private UUID botUserId;

    @Column(name = "chat_id", nullable = false)
    private UUID chatId;

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "payment_invoice_id", nullable = false)
    private UUID paymentInvoiceId;

    @Column(name = "from_user_id", nullable = false)
    private UUID fromUserId;

    @Column(name = "payment_intent_id")
    private UUID paymentIntentId;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "PENDING";

    @Column(name = "answer_text", length = 255)
    private String answerText;

    @Column(name = "requested_name", length = 120)
    private String requestedName;

    @Column(name = "requested_phone_number", length = 64)
    private String requestedPhoneNumber;

    @Column(name = "requested_email", length = 120)
    private String requestedEmail;

    @Column(name = "shipping_address_json", columnDefinition = "text")
    private String shippingAddressJson;

    @Column(name = "shipping_option_id", length = 64)
    private String shippingOptionId;

    @Column(name = "shipping_option_title", length = 120)
    private String shippingOptionTitle;

    @Column(name = "shipping_option_amount_units")
    private Long shippingOptionAmountUnits;

    @Column(name = "tip_amount_units")
    private Long tipAmountUnits;

    @Column(name = "total_amount_units")
    private Long totalAmountUnits;

    @Column(name = "receipt_id")
    private UUID receiptId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "answered_at")
    private Instant answeredAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (status == null) {
            status = "PENDING";
        }
        if (createdAt == null) {
            createdAt = now;
        }
    }

    @PreUpdate
    void preUpdate() {
        if (status == null) {
            status = "PENDING";
        }
    }
}
