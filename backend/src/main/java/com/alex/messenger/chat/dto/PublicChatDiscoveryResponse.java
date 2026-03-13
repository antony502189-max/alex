package com.alex.messenger.chat.dto;

import java.time.Instant;
import java.util.UUID;

public record PublicChatDiscoveryResponse(
        UUID chatId,
        String chatType,
        String title,
        String photoUrl,
        Instant photoAccessExpiresAt,
        String publicUsername,
        String about,
        boolean forumEnabled,
        long memberCount,
        boolean joinRequiresApproval,
        boolean joined
) {
}
