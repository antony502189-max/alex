package com.alex.messenger.payments.dto;

import java.time.Instant;
import java.util.UUID;

public record PaymentTransactionResponse(
        UUID transactionId,
        UUID walletUserId,
        UUID counterpartyUserId,
        UUID invoiceId,
        UUID paymentIntentId,
        String transactionType,
        String direction,
        long amountUnits,
        long balanceAfterUnits,
        String currencyCode,
        String description,
        Instant createdAt
) {
}
