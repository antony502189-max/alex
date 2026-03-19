package com.alex.messenger.chat.suggested.dto;

import com.alex.messenger.message.dto.MessageContactCardPayload;
import com.alex.messenger.message.dto.MessageLocationPayload;
import com.alex.messenger.message.dto.MessageTextEntityPayload;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SuggestedPostResponse(
        UUID suggestedPostId,
        UUID chatId,
        UUID submittedByUserId,
        String status,
        UUID reviewedByUserId,
        String declineReason,
        UUID publishedMessageId,
        String text,
        List<MessageTextEntityPayload> entities,
        String messageType,
        String caption,
        boolean silent,
        MessageLocationPayload location,
        MessageContactCardPayload contactCard,
        UUID stickerId,
        List<UUID> attachmentIds,
        SuggestedPostPaymentResponse payment,
        Instant createdAt,
        Instant updatedAt,
        Instant approvedAt,
        Instant declinedAt
) {
}
