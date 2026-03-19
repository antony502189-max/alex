package com.alex.messenger.story.dto;

import java.time.Instant;
import java.util.UUID;

public record StoryLiveSessionResponse(
        UUID liveSessionId,
        UUID storyId,
        UUID ownerUserId,
        UUID ownerChatId,
        String status,
        boolean donationsEnabled,
        String donationProvider,
        String donationCurrency,
        String donationEventHookUrl,
        long donationEventsCount,
        long donationsTotalMinor,
        Instant startedAt,
        Instant endedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
