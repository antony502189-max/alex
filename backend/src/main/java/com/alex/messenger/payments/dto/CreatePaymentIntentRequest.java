package com.alex.messenger.payments.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreatePaymentIntentRequest(
        @NotNull UUID invoiceId
) {
}
