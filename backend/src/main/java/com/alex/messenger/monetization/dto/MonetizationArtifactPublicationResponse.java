package com.alex.messenger.monetization.dto;

import java.time.Instant;
import java.util.UUID;

public record MonetizationArtifactPublicationResponse(
        UUID publicationId,
        UUID artifactId,
        UUID channelChatId,
        UUID targetChatId,
        UUID publishedByUserId,
        String deliveryMode,
        String note,
        UUID publishedMessageId,
        Instant publishedAt
) {
}
