package com.alex.messenger.bot;

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
@Table(name = "bot_payment_invoices")
public class BotPaymentInvoiceEntity {

    @Id
    @Column(name = "payment_invoice_id", nullable = false)
    private UUID paymentInvoiceId;

    @Column(name = "bot_user_id", nullable = false)
    private UUID botUserId;

    @Column(name = "chat_id", nullable = false)
    private UUID chatId;

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "payer_user_id", nullable = false)
    private UUID payerUserId;

    @Column(name = "invoice_payload", nullable = false, length = 255)
    private String invoicePayload;

    @Column(name = "pay_button_text", nullable = false, length = 64)
    private String payButtonText;

    @Column(name = "provider_token", length = 128)
    private String providerToken;

    @Column(name = "provider_data_json", nullable = false, columnDefinition = "text")
    private String providerDataJson = "{}";

    @Column(name = "need_name", nullable = false)
    private boolean needName;

    @Column(name = "need_phone_number", nullable = false)
    private boolean needPhoneNumber;

    @Column(name = "need_email", nullable = false)
    private boolean needEmail;

    @Column(name = "need_shipping_address", nullable = false)
    private boolean needShippingAddress;

    @Column(name = "flexible", nullable = false)
    private boolean flexible;

    @Column(name = "max_tip_amount_units")
    private Long maxTipAmountUnits;

    @Column(name = "suggested_tip_amounts_json", nullable = false, columnDefinition = "text")
    private String suggestedTipAmountsJson = "[]";

    @Column(name = "shipping_options_json", nullable = false, columnDefinition = "text")
    private String shippingOptionsJson = "[]";

    @Column(name = "successful_payment_message_id")
    private UUID successfulPaymentMessageId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (providerDataJson == null) {
            providerDataJson = "{}";
        }
        if (suggestedTipAmountsJson == null) {
            suggestedTipAmountsJson = "[]";
        }
        if (shippingOptionsJson == null) {
            shippingOptionsJson = "[]";
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
