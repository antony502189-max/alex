package com.alex.messenger.bot.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record CompleteBotPreCheckoutRequest(
        @Size(max = 120) String payerName,
        @Size(max = 64) String phoneNumber,
        @Email @Size(max = 120) String email,
        @Valid BotPaymentShippingAddressPayload shippingAddress,
        @Size(max = 64) String shippingOptionId,
        Long tipAmountUnits
) {
}
