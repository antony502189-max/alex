package com.alex.messenger.chat.channeldm.dto;

import java.time.Instant;
import java.util.UUID;

public record ChannelDirectMessageTopicResponse(
        UUID topicId,
        UUID channelChatId,
        UUID directChatId,
        UUID participantUserId,
        String participantDisplayName,
        String participantUsername,
        String title,
        Instant createdAt,
        Instant updatedAt,
        Instant lastMessageAt
) {
}
