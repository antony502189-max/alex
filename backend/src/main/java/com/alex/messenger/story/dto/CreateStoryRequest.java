package com.alex.messenger.story.dto;

import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateStoryRequest(
        @Size(max = 500) String text,
        @Size(max = 16) String backgroundFrom,
        @Size(max = 16) String backgroundTo,
        @Size(max = 16) String textColor,
        @Size(max = 16) String audience,
        List<UUID> allowedViewerUserIds,
        UUID ownerChatId
) {
}
