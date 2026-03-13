package com.alex.messenger.chat.dto;

import java.time.Instant;
import java.util.UUID;

public record ForumTopicResponse(
        UUID topicId,
        UUID chatId,
        String title,
        String iconEmoji,
        boolean generalTopic,
        boolean closed,
        boolean hidden,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt,
        Instant lastMessageAt
) {
}
