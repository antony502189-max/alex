package com.alex.messenger.message.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SendInlineBotResultRequest(
        UUID chatId,
        UUID recipientUserId,
        UUID topicId,
        UUID replyToMessageId,
        @NotBlank String botUsername,
        @NotBlank String resultId,
        @Size(max = 255) String query,
        UUID clientMessageId
) {

    @JsonIgnore
    @AssertTrue(message = "chatId or recipientUserId is required")
    public boolean isTargetSpecified() {
        return MessageRequestValidationSupport.hasTarget(chatId, recipientUserId);
    }
}
