package com.alex.messenger.monetization.dto;

import java.util.UUID;

public record AssignMonetizationArtifactSubscriptionAlertRequest(
        UUID ownerUserId,
        String note
) {
}
