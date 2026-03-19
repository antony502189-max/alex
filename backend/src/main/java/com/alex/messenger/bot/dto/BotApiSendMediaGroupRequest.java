package com.alex.messenger.bot.dto;

import com.alex.messenger.message.dto.MessageTextEntityPayload;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record BotApiSendMediaGroupRequest(
        UUID chatId,
        UUID recipientUserId,
        UUID topicId,
        UUID replyToMessageId,
        @Size(max = 4000) String caption,
        List<@NotNull @Valid MessageTextEntityPayload> entities,
        @NotEmpty @Size(min = 2, max = 10) List<@NotNull UUID> attachmentIds,
        Boolean silent,
        UUID clientMessageId,
        List<@NotNull @Valid BotApiMessageActionRequest> actions
) {

    @JsonIgnore
    @AssertTrue(message = "chatId or recipientUserId is required")
    public boolean hasTarget() {
        return chatId != null || recipientUserId != null;
    }
}
