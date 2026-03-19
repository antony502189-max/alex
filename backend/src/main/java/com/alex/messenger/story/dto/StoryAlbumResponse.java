package com.alex.messenger.story.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StoryAlbumResponse(
        UUID albumId,
        UUID ownerUserId,
        UUID ownerChatId,
        String title,
        UUID coverStoryId,
        int position,
        Instant createdAt,
        Instant updatedAt,
        int storiesCount,
        List<StoryResponse> stories
) {
}
