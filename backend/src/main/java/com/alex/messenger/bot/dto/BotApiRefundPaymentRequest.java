package com.alex.messenger.bot.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record BotApiRefundPaymentRequest(
        @NotNull UUID receiptId,
        @Size(max = 255) String reason
) {
}
