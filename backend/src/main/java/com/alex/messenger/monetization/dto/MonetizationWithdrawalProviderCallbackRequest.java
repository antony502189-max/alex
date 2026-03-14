package com.alex.messenger.monetization.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.UUID;

public record MonetizationWithdrawalProviderCallbackRequest(
        UUID withdrawalId,
        String providerReference,
        @NotBlank String providerStatus,
        String callbackType,
        String failureReason,
        Map<String, Object> payload
) {
}
