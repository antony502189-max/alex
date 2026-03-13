package com.alex.messenger.message.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record SendMessageRequest(
        UUID chatId,
        UUID recipientUserId,
        UUID topicId,
        UUID replyToMessageId,
        @Size(max = 4000) String text,
        @Size(max = 4000) String caption,
        String messageType,
        @Valid List<MessageTextEntityPayload> entities,
        @Valid MessageLocationPayload location,
        @Valid MessageContactCardPayload contactCard,
        List<UUID> attachmentIds,
        UUID stickerId,
        Boolean silent,
        UUID clientMessageId
) {
}
