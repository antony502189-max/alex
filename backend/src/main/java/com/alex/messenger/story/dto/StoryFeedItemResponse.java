package com.alex.messenger.story.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StoryFeedItemResponse(
        UUID ownerUserId,
        String ownerDisplayName,
        String ownerUsername,
        boolean own,
        boolean hasUnviewed,
        Instant latestStoryAt,
        List<StoryResponse> stories
) {
}
