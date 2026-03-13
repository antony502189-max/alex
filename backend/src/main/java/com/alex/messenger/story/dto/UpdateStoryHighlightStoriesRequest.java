package com.alex.messenger.story.dto;

import java.util.List;
import java.util.UUID;

public record UpdateStoryHighlightStoriesRequest(
        List<UUID> storyIds,
        UUID coverStoryId
) {
}
