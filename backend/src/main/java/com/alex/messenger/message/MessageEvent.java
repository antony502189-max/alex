package com.alex.messenger.message;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MessageEvent(
        UUID chatId,
        UUID messageId,
        UUID clientMessageId,
        UUID senderId,
        List<UUID> recipientIds,
        UUID viaBotUserId,
        UUID topicId,
        UUID threadRootMessageId,
        UUID discussionChatId,
        UUID discussionRootMessageId,
        Instant createdAt,
        String ciphertext,
        String nonce,
        int keyVersion,
        UUID replyToMessageId,
        UUID forwardedFromChatId,
        UUID forwardedFromMessageId,
        UUID pollId,
        UUID stickerId,
        List<UUID> attachmentIds,
        String deliveryStatus,
        Instant deliveredAt,
        Instant readAt,
        Instant expiresAt,
        Instant editedAt,
        Instant deletedAt
) {
}
