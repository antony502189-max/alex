package com.alex.messenger.chat.dto;

import java.time.Instant;
import java.util.UUID;

public record ClearHistoryResponse(
        UUID chatId,
        UUID topicId,
        UUID upToMessageId,
        int clearedMessageCount,
        Instant clearedAt
) {
}
