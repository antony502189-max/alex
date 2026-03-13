package com.alex.messenger.payments.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PaymentInvoiceResponse(
        UUID invoiceId,
        UUID createdByUserId,
        UUID recipientUserId,
        String title,
        String description,
        long amountUnits,
        String currencyCode,
        String status,
        Map<String, String> metadata,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt
) {
}
