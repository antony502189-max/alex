package com.alex.messenger.message.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AcknowledgeMessageDeliveryResponse(
        UUID sessionId,
        UUID chatId,
        UUID upToMessageId,
        List<UUID> deliveredMessageIds,
        Instant acknowledgedAt
) {
}
