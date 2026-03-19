package com.alex.messenger.message.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreateRepeatingMessageRequest(
        UUID chatId,
        UUID recipientUserId,
        UUID topicId,
        UUID replyToMessageId,
        @Size(max = 4000) String text,
        @Size(max = 4000) String caption,
        String messageType,
        @Valid List<MessageTextEntityPayload> entities,
        @Valid MessageLocationPayload location,
        @Valid MessageContactCardPayload contactCard,
        List<UUID> attachmentIds,
        UUID stickerId,
        Boolean silent,
        UUID clientRuleId,
        @NotNull @Future Instant firstScheduledAt,
        @NotNull @Min(1) @Max(10080) Integer intervalMinutes,
        @Min(1) @Max(365) Integer maxOccurrences
) {
}
