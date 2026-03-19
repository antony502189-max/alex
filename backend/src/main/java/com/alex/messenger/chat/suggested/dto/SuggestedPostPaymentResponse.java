package com.alex.messenger.chat.suggested.dto;

import java.time.Instant;
import java.util.UUID;

public record SuggestedPostPaymentResponse(
        UUID paymentId,
        UUID invoiceId,
        UUID paymentIntentId,
        UUID payerUserId,
        UUID recipientUserId,
        long amountUnits,
        String currencyCode,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
