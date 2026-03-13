package com.alex.messenger.payments.dto;

import jakarta.validation.constraints.Size;

public record UpdatePaymentIntentRequest(
        @Size(max = 255) String reason
) {
}
