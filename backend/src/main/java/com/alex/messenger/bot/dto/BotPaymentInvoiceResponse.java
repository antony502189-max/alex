package com.alex.messenger.bot.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BotPaymentInvoiceResponse(
        UUID paymentInvoiceId,
        UUID botUserId,
        UUID chatId,
        UUID messageId,
        UUID payerUserId,
        String title,
        String description,
        long amountUnits,
        String currencyCode,
        String status,
        String invoicePayload,
        String payButtonText,
        boolean needName,
        boolean needPhoneNumber,
        boolean needEmail,
        boolean needShippingAddress,
        boolean flexible,
        Long maxTipAmountUnits,
        List<Long> suggestedTipAmounts,
        List<BotPaymentShippingOptionPayload> shippingOptions,
        UUID successfulPaymentMessageId,
        Instant createdAt,
        Instant expiresAt
) {
}
