package com.alex.messenger.bot.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BotPreCheckoutQueryResponse(
        UUID preCheckoutQueryId,
        UUID botUserId,
        UUID chatId,
        UUID messageId,
        UUID paymentInvoiceId,
        UUID fromUserId,
        UUID paymentIntentId,
        String title,
        String description,
        long amountUnits,
        String currencyCode,
        String status,
        String invoicePayload,
        boolean needName,
        boolean needPhoneNumber,
        boolean needEmail,
        boolean needShippingAddress,
        boolean flexible,
        Long maxTipAmountUnits,
        List<Long> suggestedTipAmounts,
        List<BotPaymentShippingOptionPayload> shippingOptions,
        String requestedName,
        String requestedPhoneNumber,
        String requestedEmail,
        BotPaymentShippingAddressPayload shippingAddress,
        String shippingOptionId,
        String shippingOptionTitle,
        long shippingAmountUnits,
        long tipAmountUnits,
        long totalAmountUnits,
        UUID receiptId,
        String answerText,
        Instant createdAt,
        Instant answeredAt,
        Instant completedAt
) {
}
