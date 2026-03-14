package com.alex.messenger.monetization.dto;

import java.time.Instant;
import java.util.UUID;

public record MonetizationWithdrawalResponse(
        UUID withdrawalId,
        UUID channelChatId,
        UUID recipientUserId,
        UUID requestedByUserId,
        long amountUnits,
        String currencyCode,
        String destinationType,
        String destinationLabel,
        String note,
        String status,
        String providerReference,
        String providerStatus,
        String failureReason,
        Instant requestedAt,
        Instant processingAt,
        Instant providerUpdatedAt,
        Instant completedAt,
        Instant canceledAt
) {
}
