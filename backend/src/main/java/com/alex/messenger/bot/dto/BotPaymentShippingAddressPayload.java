package com.alex.messenger.bot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BotPaymentShippingAddressPayload(
        @NotBlank @Size(max = 16) String countryCode,
        @Size(max = 120) String state,
        @NotBlank @Size(max = 120) String city,
        @NotBlank @Size(max = 255) String streetLine1,
        @Size(max = 255) String streetLine2,
        @NotBlank @Size(max = 32) String postCode
) {
}
