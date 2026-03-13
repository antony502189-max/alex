package com.alex.messenger.chat.dto;

import java.time.Instant;
import java.util.UUID;

public record ChatAdminLogResponse(
        UUID eventId,
        String eventType,
        UUID actorUserId,
        String actorDisplayName,
        UUID subjectUserId,
        String subjectDisplayName,
        UUID messageId,
        UUID inviteLinkId,
        String summary,
        Instant createdAt
) {
}
