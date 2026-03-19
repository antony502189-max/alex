package com.alex.messenger.bot.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Future;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record BotApiSendInvoiceRequest(
        UUID chatId,
        UUID recipientUserId,
        @NotBlank @Size(max = 120) String title,
        @Size(max = 500) String description,
        @NotNull @Positive Long amountUnits,
        @Future Instant expiresAt,
        @NotBlank @Size(max = 255) String invoicePayload,
        @Size(max = 64) String payButtonText,
        @Size(max = 128) String providerToken,
        Map<@NotBlank @Size(max = 64) String, @NotBlank @Size(max = 255) String> providerData,
        Boolean needName,
        Boolean needPhoneNumber,
        Boolean needEmail,
        Boolean needShippingAddress,
        Boolean flexible,
        @Positive Long maxTipAmountUnits,
        List<@NotNull @Positive Long> suggestedTipAmounts,
        List<@NotNull @Valid BotPaymentShippingOptionPayload> shippingOptions
) {

    @JsonIgnore
    @AssertTrue(message = "chatId or recipientUserId is required")
    public boolean hasTarget() {
        return chatId != null || recipientUserId != null;
    }

    @JsonIgnore
    @AssertTrue(message = "Suggested tip amounts require max tip amount")
    public boolean hasValidTipConfiguration() {
        return suggestedTipAmounts == null || suggestedTipAmounts.isEmpty() || maxTipAmountUnits != null;
    }

    @JsonIgnore
    @AssertTrue(message = "Shipping options require shipping address")
    public boolean hasValidShippingOptionsConfiguration() {
        return !hasShippingOptions() || Boolean.TRUE.equals(needShippingAddress);
    }

    @JsonIgnore
    @AssertTrue(message = "Flexible invoices require shipping address")
    public boolean hasFlexibleShippingAddressConfiguration() {
        return !Boolean.TRUE.equals(flexible) || Boolean.TRUE.equals(needShippingAddress);
    }

    @JsonIgnore
    @AssertTrue(message = "Flexible invoices require shipping options")
    public boolean hasFlexibleShippingOptionsConfiguration() {
        return !Boolean.TRUE.equals(flexible) || hasShippingOptions();
    }

    private boolean hasShippingOptions() {
        return shippingOptions != null && !shippingOptions.isEmpty();
    }
}
