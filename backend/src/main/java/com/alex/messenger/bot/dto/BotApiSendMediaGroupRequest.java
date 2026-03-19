package com.alex.messenger.bot.dto;

import com.alex.messenger.message.dto.MessageTextEntityPayload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record BotApiSendMediaGroupRequest(
        UUID chatId,
        UUID recipientUserId,
        UUID topicId,
        UUID replyToMessageId,
        @Size(max = 4000) String caption,
        @Valid List<MessageTextEntityPayload> entities,
        @NotEmpty @Size(min = 2, max = 10) List<UUID> attachmentIds,
        Boolean silent,
        UUID clientMessageId,
        @Valid List<BotApiMessageActionRequest> actions
) {
}
