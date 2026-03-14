package com.alex.messenger.monetization.dto;

import java.util.Map;
import java.util.UUID;

public record MonetizationProviderStatusUpdateRequest(
        UUID withdrawalId,
        String providerReference,
        String providerStatus,
        String callbackType,
        String failureReason,
        Map<String, Object> payload
) {
}
