package com.alex.messenger.bot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BotPaymentShippingOptionPayload(
        @NotBlank @Size(max = 64) String optionId,
        @NotBlank @Size(max = 120) String title,
        @NotNull Long amountUnits
) {
}
