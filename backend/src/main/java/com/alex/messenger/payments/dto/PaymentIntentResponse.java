package com.alex.messenger.payments.dto;

import java.time.Instant;
import java.util.UUID;

public record PaymentIntentResponse(
        UUID paymentIntentId,
        UUID invoiceId,
        UUID payerUserId,
        UUID recipientUserId,
        long amountUnits,
        String currencyCode,
        String status,
        String canceledReason,
        String refundedReason,
        Instant createdAt,
        Instant confirmedAt,
        Instant canceledAt,
        Instant refundedAt,
        Instant updatedAt
) {
}
