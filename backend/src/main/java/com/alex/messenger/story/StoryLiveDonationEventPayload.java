package com.alex.messenger.story;

import java.time.Instant;
import java.util.UUID;

public record StoryLiveDonationEventPayload(
        String eventType,
        UUID commentId,
        UUID liveSessionId,
        UUID storyId,
        String storyOwnerType,
        UUID ownerUserId,
        UUID ownerChatId,
        String ownerDisplayName,
        String ownerUsername,
        UUID publisherUserId,
        String publisherDisplayName,
        String publisherUsername,
        UUID authorUserId,
        String authorDisplayName,
        String authorUsername,
        String message,
        Long donationAmountMinor,
        String donationCurrency,
        String donationProvider,
        long donationEventsCount,
        long donationsTotalMinor,
        Instant sessionStartedAt,
        Instant commentCreatedAt
) {
}
