package com.alex.messenger.chat.dto;

import java.util.UUID;

public record ChatInboxEventResponse(
        String eventType,
        UUID chatId,
        ChatSummaryResponse chat
) {
}
