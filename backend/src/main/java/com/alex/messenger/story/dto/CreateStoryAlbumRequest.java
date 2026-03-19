package com.alex.messenger.story.dto;

import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateStoryAlbumRequest(
        @Size(max = 120) String title,
        UUID coverStoryId,
        Integer position,
        List<UUID> storyIds,
        UUID ownerChatId
) {
}
