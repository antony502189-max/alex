package com.alex.messenger.payments;

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
@Table(name = "payment_wallet_transactions")
public class PaymentWalletTransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "wallet_user_id", nullable = false)
    private UUID walletUserId;

    @Column(name = "counterparty_user_id")
    private UUID counterpartyUserId;

    @Column(name = "invoice_id")
    private UUID invoiceId;

    @Column(name = "payment_intent_id")
    private UUID paymentIntentId;

    @Column(name = "transaction_type", nullable = false, length = 32)
    private String transactionType;

    @Column(name = "direction", nullable = false, length = 16)
    private String direction;

    @Column(name = "amount_units", nullable = false)
    private Long amountUnits;

    @Column(name = "balance_after_units", nullable = false)
    private Long balanceAfterUnits;

    @Column(name = "currency_code", nullable = false, length = 16)
    private String currencyCode = "XTR";

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (currencyCode == null) {
            currencyCode = "XTR";
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
