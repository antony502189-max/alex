package com.alex.messenger.monetization.dto;

import java.time.Instant;
import java.util.UUID;

public record MonetizationAlertDigestRunResponse(
        UUID alertDigestRunId,
        UUID channelChatId,
        UUID generatedByUserId,
        String triggerMode,
        int openAlertCount,
        int affectedSubscriptionCount,
        UUID artifactId,
        UUID publishedMessageId,
        Instant createdAt
) {
}
