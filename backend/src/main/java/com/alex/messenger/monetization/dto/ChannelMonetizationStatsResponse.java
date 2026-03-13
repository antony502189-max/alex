package com.alex.messenger.monetization.dto;

import java.util.UUID;

public record ChannelMonetizationStatsResponse(
        UUID channelChatId,
        int totalSponsoredMessages,
        int draftCount,
        int activeCount,
        int pausedCount,
        int completedCount,
        int publishedCount,
        long totalBudgetUnits,
        long totalSpentUnits,
        long remainingBudgetUnits,
        long impressionsCount,
        long clicksCount,
        double clickThroughRatePercent
) {
}
