package com.alex.messenger.user.dto;

import java.util.List;
import java.util.UUID;

public record UpdatePrivacyExceptionsRequest(
        List<UUID> phoneAllowedUserIds,
        List<UUID> phoneDisallowedUserIds,
        List<UUID> lastSeenAllowedUserIds,
        List<UUID> lastSeenDisallowedUserIds,
        List<UUID> storyAllowedUserIds,
        List<UUID> storyDisallowedUserIds
) {
}
