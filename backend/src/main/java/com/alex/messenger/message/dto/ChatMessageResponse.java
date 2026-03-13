package com.alex.messenger.message.dto;

import com.alex.messenger.poll.dto.PollResponse;
import com.alex.messenger.sticker.dto.StickerResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChatMessageResponse(
        UUID chatId,
        UUID messageId,
        UUID clientMessageId,
        UUID senderId,
        String displaySenderName,
        String displaySenderPhotoUrl,
        Instant displaySenderPhotoAccessExpiresAt,
        boolean anonymousSender,
        UUID recipientId,
        UUID viaBotUserId,
        UUID topicId,
        UUID threadRootMessageId,
        UUID discussionChatId,
        UUID discussionRootMessageId,
        int commentCount,
        String text,
        List<MessageTextEntityPayload> entities,
        String messageType,
        String caption,
        boolean silent,
        MessageLocationPayload location,
        MessageContactCardPayload contactCard,
        MessageServicePayload serviceMessage,
        Instant createdAt,
        UUID replyToMessageId,
        UUID forwardedFromChatId,
        UUID forwardedFromMessageId,
        PollResponse poll,
        StickerResponse sticker,
        List<MessageAttachmentResponse> attachments,
        List<MessageReactionSummary> reactions,
        String deliveryStatus,
        Instant deliveredAt,
        Instant readAt,
        Instant expiresAt,
        Instant editedAt,
        Instant deletedAt
) {
}
