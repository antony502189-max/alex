package com.alex.messenger.monetization.dto;

import java.time.Instant;
import java.util.UUID;

public record MonetizationArtifactAlertTriageResponse(
        UUID alertId,
        UUID subscriptionId,
        UUID channelChatId,
        UUID targetChatId,
        UUID publishedMessageId,
        Instant triagedAt,
        boolean manual
) {
}
