package com.alex.messenger.story.dto;

import java.time.Instant;
import java.util.UUID;

public record StoryLiveCommentResponse(
        UUID commentId,
        UUID storyId,
        UUID liveSessionId,
        UUID authorUserId,
        String authorDisplayName,
        String authorUsername,
        String message,
        Long donationAmountMinor,
        String donationCurrency,
        Instant createdAt
) {
}
