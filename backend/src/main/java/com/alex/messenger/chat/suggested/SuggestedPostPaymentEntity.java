package com.alex.messenger.chat.suggested;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "suggested_post_payments")
public class SuggestedPostPaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "suggested_post_id", nullable = false, unique = true)
    private UUID suggestedPostId;

    @Column(name = "invoice_id", nullable = false, unique = true)
    private UUID invoiceId;

    @Column(name = "payment_intent_id")
    private UUID paymentIntentId;

    @Column(name = "payer_user_id", nullable = false)
    private UUID payerUserId;

    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    @Column(name = "amount_units", nullable = false)
    private Long amountUnits;

    @Column(name = "currency_code", nullable = false, length = 16)
    private String currencyCode = "XTR";

    @Column(name = "status", nullable = false, length = 32)
    private String status = "OPEN";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (currencyCode == null) {
            currencyCode = "XTR";
        }
        if (status == null) {
            status = "OPEN";
        }
        if (version == null) {
            version = 0L;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
