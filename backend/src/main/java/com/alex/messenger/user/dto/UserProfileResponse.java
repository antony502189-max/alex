package com.alex.messenger.user.dto;

import java.time.Instant;
import java.util.UUID;

public record UserProfileResponse(
        UUID userId,
        String phoneNumber,
        String displayName,
        String username,
        boolean bot,
        String botDescription,
        boolean botSupportsInline,
        String botWebAppUrl,
        String about,
        String photoUrl,
        Instant photoAccessExpiresAt,
        String phonePrivacy,
        String lastSeenPrivacy,
        String storyPrivacy,
        String preferredLanguage,
        String translationTargetLanguage,
        Instant lastSeenAt,
        boolean online
) {
}
