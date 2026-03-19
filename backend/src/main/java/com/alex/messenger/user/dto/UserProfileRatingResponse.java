package com.alex.messenger.user.dto;

import java.time.Instant;
import java.util.UUID;

public record UserProfileRatingResponse(
        UUID userId,
        long ratingScore,
        String ratingLevel,
        long receivedGiftCount,
        long sentGiftCount,
        long receivedGiftPremiumDays,
        long sentGiftPremiumDays,
        long starsReceivedUnits,
        long starsSpentUnits,
        long successfulTransactionCount,
        Instant lastRecomputedAt
) {
}
