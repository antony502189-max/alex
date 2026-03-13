package com.alex.messenger.chat.dto;

import java.time.Instant;
import java.util.UUID;

public record ChatAnalyticsResponse(
        UUID chatId,
        String chatType,
        long memberCount,
        long adminCount,
        long restrictedCount,
        long bannedCount,
        long pendingJoinRequestCount,
        long activeInviteLinkCount,
        long messagesLast24h,
        long reactionsLast24h,
        long commentsLast24h,
        Instant lastMessageAt
) {
}
