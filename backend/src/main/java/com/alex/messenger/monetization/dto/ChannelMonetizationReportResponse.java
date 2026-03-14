package com.alex.messenger.monetization.dto;

import java.time.Instant;
import java.util.UUID;

public record ChannelMonetizationReportResponse(
        UUID channelChatId,
        long totalRevenueUnits,
        long totalSettledUnits,
        long outstandingPayoutUnits,
        long availableWithdrawalUnits,
        long totalWithdrawnUnits,
        long pendingWithdrawalUnits,
        long failedWithdrawalUnits,
        int totalWithdrawals,
        int pendingWithdrawalCount,
        int processingWithdrawalCount,
        int completedWithdrawalCount,
        int failedWithdrawalCount,
        int canceledWithdrawalCount,
        double averageRevenuePerCampaignUnits,
        Instant lastPayoutAt,
        Instant lastWithdrawalAt,
        Instant lastReconciliationAt
) {
}
