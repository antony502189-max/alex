package com.alex.messenger.bot.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record BotPaymentReceiptResponse(
        UUID receiptId,
        UUID paymentInvoiceId,
        UUID paymentIntentId,
        UUID preCheckoutQueryId,
        UUID botUserId,
        UUID chatId,
        UUID invoiceMessageId,
        UUID serviceMessageId,
        UUID refundMessageId,
        UUID payerUserId,
        String title,
        String description,
        String invoicePayload,
        String currencyCode,
        long baseAmountUnits,
        long shippingAmountUnits,
        long tipAmountUnits,
        long totalAmountUnits,
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
        Map<String, String> providerData,
        Instant createdAt,
        Instant refundedAt
) {
}
