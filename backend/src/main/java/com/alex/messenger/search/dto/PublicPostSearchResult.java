package com.alex.messenger.search.dto;

import java.time.Instant;
import java.util.UUID;

public record PublicPostSearchResult(
        UUID chatId,
        String channelTitle,
        String channelPublicUsername,
        String channelAbout,
        UUID messageId,
        UUID senderId,
        UUID topicId,
        UUID discussionChatId,
        UUID discussionRootMessageId,
        String excerpt,
        String messageType,
        int attachmentCount,
        boolean hasMedia,
        Instant createdAt
) {
}
