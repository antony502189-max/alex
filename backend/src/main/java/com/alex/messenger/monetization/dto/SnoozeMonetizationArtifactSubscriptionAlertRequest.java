package com.alex.messenger.monetization.dto;

import java.time.Instant;

public record SnoozeMonetizationArtifactSubscriptionAlertRequest(
        Integer snoozeMinutes,
        Instant snoozedUntil
) {
}
