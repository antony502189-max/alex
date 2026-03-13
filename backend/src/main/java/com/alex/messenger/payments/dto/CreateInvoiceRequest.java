package com.alex.messenger.payments.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record CreateInvoiceRequest(
        @NotNull UUID recipientUserId,
        @NotBlank @Size(max = 120) String title,
        @Size(max = 500) String description,
        @NotNull Long amountUnits,
        Instant expiresAt,
        Map<String, String> metadata
) {
}
