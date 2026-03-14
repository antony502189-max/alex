package com.alex.messenger.monetization.dto;

import java.time.Instant;
import java.util.UUID;

public record MonetizationArtifactAlertReminderResponse(
        UUID alertId,
        UUID subscriptionId,
        UUID channelChatId,
        UUID targetChatId,
        UUID routedTargetChatId,
        String reminderType,
        UUID publishedMessageId,
        Instant remindedAt,
        int reminderCount
) {
}
