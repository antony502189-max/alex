package com.alex.messenger.chat.dto;

import com.alex.messenger.message.dto.ChatMessageResponse;
import java.time.Instant;
import java.util.UUID;

public record PinnedMessageHistoryResponse(
        UUID pinEventId,
        UUID chatId,
        UUID messageId,
        UUID pinnedByUserId,
        String pinnedByDisplayName,
        Instant pinnedAt,
        boolean active,
        Instant unpinnedAt,
        ChatMessageResponse message
) {
}
