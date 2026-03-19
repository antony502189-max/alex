package com.alex.messenger.bot.dto;

import com.alex.messenger.message.dto.MessageTextEntityPayload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record BotApiSendAttachmentMessageRequest(
        UUID chatId,
        UUID recipientUserId,
        UUID topicId,
        UUID replyToMessageId,
        @Size(max = 4000) String caption,
        @Valid List<MessageTextEntityPayload> entities,
        @NotNull UUID attachmentId,
        Boolean silent,
        UUID clientMessageId,
        @Valid List<BotApiMessageActionRequest> actions
) {
}
