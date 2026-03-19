package com.alex.messenger.message.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreatePollMessageRequest(
        UUID chatId,
        UUID recipientUserId,
        UUID topicId,
        UUID replyToMessageId,
        @NotBlank @Size(max = 255) String question,
        @NotNull @Size(min = 2, max = 10) List<@NotBlank @Size(max = 160) String> options,
        boolean multipleChoice,
        Boolean quiz,
        Integer correctOptionIndex,
        @Size(max = 255) String explanation,
        Boolean anonymousVotes,
        Instant closeAt,
        UUID clientMessageId
) {

    @JsonIgnore
    @AssertTrue(message = "chatId or recipientUserId is required")
    public boolean isTargetSpecified() {
        return MessageRequestValidationSupport.hasTarget(chatId, recipientUserId);
    }
}
