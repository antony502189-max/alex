package com.alex.messenger.message.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
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

    @JsonIgnore
    @AssertTrue(message = "chatId or recipientUserId is required")
    public boolean isTargetSpecified() {
        return MessageRequestValidationSupport.hasTarget(chatId, recipientUserId);
    }
}
