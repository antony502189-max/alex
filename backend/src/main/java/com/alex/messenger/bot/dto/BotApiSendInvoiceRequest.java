package com.alex.messenger.bot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record BotApiSendInvoiceRequest(
        UUID chatId,
        UUID recipientUserId,
        @NotBlank @Size(max = 120) String title,
        @Size(max = 500) String description,
        @NotNull Long amountUnits,
        Instant expiresAt,
        @NotBlank @Size(max = 255) String invoicePayload,
        @Size(max = 64) String payButtonText,
        @Size(max = 128) String providerToken,
        Map<String, String> providerData,
        Boolean needName,
        Boolean needPhoneNumber,
        Boolean needEmail,
        Boolean needShippingAddress,
        Boolean flexible,
        Long maxTipAmountUnits,
        List<Long> suggestedTipAmounts,
        List<BotPaymentShippingOptionPayload> shippingOptions
) {
}
