package com.alex.messenger.chat.dto;

import java.time.Instant;
import java.util.UUID;

public record JoinChatResultResponse(
        String status,
        ChatSummaryResponse chat,
        UUID chatId,
        String title,
        String publicUsername,
        Instant requestedAt
) {
}
