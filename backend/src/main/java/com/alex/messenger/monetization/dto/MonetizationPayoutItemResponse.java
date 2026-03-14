package com.alex.messenger.monetization.dto;

import java.time.Instant;
import java.util.UUID;

public record MonetizationPayoutItemResponse(
        UUID sponsoredMessageId,
        long settledUnits,
        Instant createdAt
) {
}
