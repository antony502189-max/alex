package com.alex.messenger.message.dto;

import java.util.UUID;

public record SendInlineBotResultRequest(
        UUID chatId,
        UUID recipientUserId,
        UUID topicId,
        UUID replyToMessageId,
        String botUsername,
        String resultId,
        String query,
        UUID clientMessageId
) {
}
