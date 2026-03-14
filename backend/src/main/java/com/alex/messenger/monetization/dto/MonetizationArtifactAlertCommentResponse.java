package com.alex.messenger.monetization.dto;

import java.time.Instant;
import java.util.UUID;

public record MonetizationArtifactAlertCommentResponse(
        UUID commentId,
        UUID alertId,
        UUID subscriptionId,
        UUID channelChatId,
        UUID authorUserId,
        String body,
        Instant createdAt
) {
}
