package com.alex.messenger.monetization.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MonetizationOwnerReminderDigestIssueSummaryResponse(
        UUID channelChatId,
        int totalIssues,
        int backoffSubscriptions,
        int autoPausedSubscriptions,
        int dueRetrySubscriptions,
        Instant latestFailureAt,
        List<MonetizationOwnerReminderDigestIssueOwnerResponse> owners
) {
}
