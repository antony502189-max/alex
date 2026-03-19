package com.alex.messenger.story.dto;

import java.util.UUID;

public record StoryInteractionEventResponse(
        String eventType,
        UUID storyId,
        UUID ownerUserId,
        UUID ownerChatId,
        StoryInteractionResponse interaction,
        StoryInteractionSummaryResponse summary,
        int unreadInteractionsCount,
        int storyUnreadInteractionsCount
) {
}
