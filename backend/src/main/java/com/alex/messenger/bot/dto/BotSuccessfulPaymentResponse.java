package com.alex.messenger.bot.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BotSuccessfulPaymentResponse(
        UUID preCheckoutQueryId,
        UUID botUserId,
        UUID chatId,
        UUID invoiceMessageId,
        UUID paymentInvoiceId,
        UUID paymentIntentId,
        UUID fromUserId,
        String title,
        String description,
        long baseAmountUnits,
        long shippingAmountUnits,
        long tipAmountUnits,
        long totalAmountUnits,
        String currencyCode,
        String invoicePayload,
        boolean needName,
        boolean needPhoneNumber,
        boolean needEmail,
        boolean needShippingAddress,
        boolean flexible,
        String payerName,
        String phoneNumber,
        String email,
        BotPaymentShippingAddressPayload shippingAddress,
        String shippingOptionId,
        String shippingOptionTitle,
        Long maxTipAmountUnits,
        List<Long> suggestedTipAmounts,
        List<BotPaymentShippingOptionPayload> shippingOptions,
        UUID receiptId,
        UUID serviceMessageId,
        UUID refundMessageId,
        Instant createdAt,
        Instant completedAt,
        Instant confirmedAt,
        Instant refundedAt
) {
}
