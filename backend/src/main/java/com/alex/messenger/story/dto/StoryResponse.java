package com.alex.messenger.story.dto;

import java.time.Instant;
import java.util.UUID;

public record StoryResponse(
        UUID storyId,
        UUID ownerUserId,
        UUID ownerChatId,
        String ownerDisplayName,
        String ownerUsername,
        String text,
        StoryMediaResponse media,
        String backgroundFrom,
        String backgroundTo,
        String textColor,
        String audience,
        Instant createdAt,
        Instant expiresAt,
        boolean expired,
        boolean viewed,
        boolean own,
        int viewsCount
) {
}
