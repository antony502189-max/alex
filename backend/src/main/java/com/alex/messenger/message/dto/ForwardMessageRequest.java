package com.alex.messenger.message.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ForwardMessageRequest(
        UUID chatId,
        UUID recipientUserId,
        UUID topicId,
        UUID replyToMessageId,
        @NotNull UUID sourceMessageId,
        UUID clientMessageId
) {
}
