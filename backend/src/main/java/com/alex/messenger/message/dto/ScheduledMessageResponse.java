package com.alex.messenger.message.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ScheduledMessageResponse(
        UUID scheduledMessageId,
        UUID clientMessageId,
        UUID chatId,
        UUID senderId,
        UUID topicId,
        UUID threadRootMessageId,
        UUID discussionChatId,
        UUID discussionRootMessageId,
        String text,
        List<MessageTextEntityPayload> entities,
        String messageType,
        String caption,
        boolean silent,
        MessageLocationPayload location,
        MessageContactCardPayload contactCard,
        MessageServicePayload serviceMessage,
        UUID replyToMessageId,
        UUID stickerId,
        List<MessageAttachmentResponse> attachments,
        Instant scheduledAt,
        Instant createdAt,
        String status
) {
}
