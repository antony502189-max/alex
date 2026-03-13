package com.alex.messenger.monetization.dto;

import java.time.Instant;
import java.util.UUID;

public record SponsoredMessageResponse(
        UUID sponsoredMessageId,
        UUID channelChatId,
        UUID sponsorUserId,
        UUID createdByUserId,
        String title,
        String messageText,
        String callToActionLabel,
        String callToActionUrl,
        long budgetUnits,
        long spentUnits,
        long remainingBudgetUnits,
        long costPerImpressionUnits,
        long costPerClickUnits,
        long impressionsCount,
        long clicksCount,
        String status,
        UUID deliveredMessageId,
        Instant createdAt,
        Instant updatedAt,
        Instant publishedAt,
        Instant activeUntil
) {
}
