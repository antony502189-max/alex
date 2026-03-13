package com.alex.messenger.business.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BusinessProfileResponse(
        UUID userId,
        boolean greetingEnabled,
        String greetingMessage,
        boolean awayEnabled,
        String awayMessage,
        List<BusinessHourSlotPayload> businessHours,
        String timeZone,
        Instant createdAt,
        Instant updatedAt
) {
}
