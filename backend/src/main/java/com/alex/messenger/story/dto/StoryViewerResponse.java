package com.alex.messenger.story.dto;

import java.time.Instant;
import java.util.UUID;

public record StoryViewerResponse(
        UUID viewerUserId,
        String displayName,
        String username,
        Instant viewedAt
) {
}
