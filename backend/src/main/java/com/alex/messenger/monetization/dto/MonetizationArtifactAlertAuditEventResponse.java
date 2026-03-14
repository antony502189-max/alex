package com.alex.messenger.monetization.dto;

import java.time.Instant;
import java.util.UUID;

public record MonetizationArtifactAlertAuditEventResponse(
        UUID eventId,
        UUID alertId,
        UUID subscriptionId,
        UUID channelChatId,
        String actionType,
        UUID actorUserId,
        UUID ownerUserId,
        String fromStatus,
        String toStatus,
        String note,
        Instant createdAt
) {
}
