package com.alex.messenger.monetization.dto;

import java.time.Instant;
import java.util.UUID;

public record MonetizationArtifactAlertTriageReminderResponse(
        UUID alertId,
        UUID subscriptionId,
        UUID channelChatId,
        UUID targetChatId,
        UUID routedTargetChatId,
        UUID publishedMessageId,
        Instant remindedAt,
        int reminderCount,
        boolean manual
) {
}
