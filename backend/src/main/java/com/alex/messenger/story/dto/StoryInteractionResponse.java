package com.alex.messenger.story.dto;

import java.time.Instant;
import java.util.UUID;

public record StoryInteractionResponse(
        UUID interactionId,
        UUID storyId,
        String type,
        UUID actorUserId,
        String actorDisplayName,
        String actorUsername,
        UUID targetUserId,
        String targetDisplayName,
        String targetUsername,
        String reaction,
        String message,
        Instant createdAt
) {
}
