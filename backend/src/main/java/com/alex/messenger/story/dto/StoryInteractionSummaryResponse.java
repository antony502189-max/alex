package com.alex.messenger.story.dto;

import java.util.UUID;

public record StoryInteractionSummaryResponse(
        UUID storyId,
        int reactionsCount,
        int repliesCount,
        int mentionsCount,
        int resharesCount,
        String viewerReaction
) {
}
