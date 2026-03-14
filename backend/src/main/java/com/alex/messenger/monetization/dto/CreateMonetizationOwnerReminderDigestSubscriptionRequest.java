package com.alex.messenger.monetization.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;

public record CreateMonetizationOwnerReminderDigestSubscriptionRequest(
        UUID targetChatId,
        String severity,
        Boolean breachedOnly,
        @Min(1) @Max(10080) Integer minIntervalMinutes,
        String note
) {
}
