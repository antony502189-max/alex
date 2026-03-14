package com.alex.messenger.monetization.dto;

import java.time.Instant;
import java.util.UUID;

public record SponsoredMessageDeliveryResponse(
        UUID sponsoredMessageId,
        UUID channelChatId,
        String title,
        String messageText,
        String callToActionLabel,
        String callToActionUrl,
        UUID deliveredMessageId,
        long remainingBudgetUnits,
        boolean impressionRecorded,
        Instant publishedAt,
        Instant activeUntil
) {
}
