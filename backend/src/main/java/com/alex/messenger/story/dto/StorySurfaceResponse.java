package com.alex.messenger.story.dto;

import java.util.List;
import java.util.UUID;

public record StorySurfaceResponse(
        UUID ownerUserId,
        UUID ownerChatId,
        String ownerType,
        String ownerDisplayName,
        String ownerUsername,
        boolean canManage,
        boolean publicSurface,
        int activeStoriesCount,
        int liveStoriesCount,
        int albumCount,
        List<UUID> activeLiveStoryIds,
        List<StoryResponse> activeStories,
        List<StoryAlbumResponse> albums
) {
}
