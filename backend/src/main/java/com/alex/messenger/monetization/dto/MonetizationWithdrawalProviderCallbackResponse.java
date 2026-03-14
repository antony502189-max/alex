package com.alex.messenger.monetization.dto;

import java.time.Instant;
import java.util.UUID;

public record MonetizationWithdrawalProviderCallbackResponse(
        UUID callbackId,
        UUID withdrawalId,
        UUID channelChatId,
        String providerReference,
        String callbackType,
        String providerStatus,
        String failureReason,
        boolean applied,
        String appliedWithdrawalStatus,
        String resultMessage,
        Instant receivedAt,
        Instant processedAt,
        String payloadJson
) {
}
