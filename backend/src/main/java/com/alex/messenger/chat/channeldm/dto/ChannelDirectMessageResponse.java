package com.alex.messenger.chat.channeldm.dto;

import com.alex.messenger.chat.dto.ChatSummaryResponse;
import java.time.Instant;
import java.util.UUID;

public record ChannelDirectMessageResponse(
        UUID linkId,
        UUID channelChatId,
        UUID directChatId,
        UUID topicId,
        UUID participantUserId,
        String participantDisplayName,
        String participantUsername,
        String status,
        String title,
        Instant createdAt,
        Instant updatedAt,
        Instant lastMessageAt,
        ChatSummaryResponse chat
) {
}
