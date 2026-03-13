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
@Table(name = "bot_payment_receipts")
public class BotPaymentReceiptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "payment_invoice_id", nullable = false)
    private UUID paymentInvoiceId;

    @Column(name = "payment_intent_id", nullable = false)
    private UUID paymentIntentId;

    @Column(name = "pre_checkout_query_id", nullable = false)
    private UUID preCheckoutQueryId;

    @Column(name = "bot_user_id", nullable = false)
    private UUID botUserId;

    @Column(name = "chat_id", nullable = false)
    private UUID chatId;

    @Column(name = "invoice_message_id", nullable = false)
    private UUID invoiceMessageId;

    @Column(name = "service_message_id")
    private UUID serviceMessageId;

    @Column(name = "refund_message_id")
    private UUID refundMessageId;

    @Column(name = "payer_user_id", nullable = false)
    private UUID payerUserId;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "invoice_payload", nullable = false, length = 255)
    private String invoicePayload;

    @Column(name = "currency_code", nullable = false, length = 16)
    private String currencyCode = "XTR";

    @Column(name = "base_amount_units", nullable = false)
    private Long baseAmountUnits;

    @Column(name = "shipping_amount_units", nullable = false)
    private Long shippingAmountUnits = 0L;

    @Column(name = "tip_amount_units", nullable = false)
    private Long tipAmountUnits = 0L;

    @Column(name = "total_amount_units", nullable = false)
    private Long totalAmountUnits;

    @Column(name = "provider_token", length = 128)
    private String providerToken;

    @Column(name = "provider_data_json", nullable = false, columnDefinition = "text")
    private String providerDataJson = "{}";

    @Column(name = "payer_name", length = 120)
    private String payerName;

    @Column(name = "phone_number", length = 64)
    private String phoneNumber;

    @Column(name = "email", length = 120)
    private String email;

    @Column(name = "shipping_address_json", columnDefinition = "text")
    private String shippingAddressJson;

    @Column(name = "shipping_option_id", length = 64)
    private String shippingOptionId;

    @Column(name = "shipping_option_title", length = 120)
    private String shippingOptionTitle;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @PrePersist
    void prePersist() {
        if (currencyCode == null) {
            currencyCode = "XTR";
        }
        if (providerDataJson == null) {
            providerDataJson = "{}";
        }
        if (shippingAmountUnits == null) {
            shippingAmountUnits = 0L;
        }
        if (tipAmountUnits == null) {
            tipAmountUnits = 0L;
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
