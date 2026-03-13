package com.alex.messenger.bot.dto;

import com.alex.messenger.message.dto.MessageTextEntityPayload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record BotApiAnswerWebAppQueryRequest(
        UUID webAppQueryId,
        @Size(max = 4000) String text,
        @Size(max = 4000) String caption,
        String messageType,
        @Valid List<MessageTextEntityPayload> entities,
        List<UUID> attachmentIds,
        UUID stickerId,
        Boolean silent
) {
}
