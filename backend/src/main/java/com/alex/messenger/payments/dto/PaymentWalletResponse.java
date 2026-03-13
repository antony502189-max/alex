package com.alex.messenger.payments.dto;

import java.time.Instant;
import java.util.UUID;

public record PaymentWalletResponse(
        UUID userId,
        long balanceUnits,
        String currencyCode,
        Instant updatedAt
) {
}
