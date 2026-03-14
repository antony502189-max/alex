package com.alex.messenger.monetization.dto;

import java.util.UUID;

public record ChannelMonetizationStatsResponse(
        UUID channelChatId,
        int totalSponsoredMessages,
        int draftCount,
        int activeCount,
        int pausedCount,
        int completedCount,
        int canceledCount,
        int publishedCount,
        long totalBudgetUnits,
        long totalSpentUnits,
        long totalEarnedUnits,
        long totalSettledUnits,
        long outstandingPayoutUnits,
        long remainingBudgetUnits,
        long impressionsCount,
        long clicksCount,
        long totalPayoutUnits,
        int uniqueSponsorCount,
        int totalPayouts,
        double clickThroughRatePercent
) {
}
