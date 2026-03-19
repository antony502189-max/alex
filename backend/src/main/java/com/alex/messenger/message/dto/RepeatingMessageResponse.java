package com.alex.messenger.message.dto;

import java.time.Instant;
import java.util.UUID;

public record RepeatingMessageResponse(
        UUID ruleId,
        UUID clientRuleId,
        UUID chatId,
        UUID senderId,
        UUID topicId,
        UUID replyToMessageId,
        UUID stickerId,
        int intervalMinutes,
        Integer maxOccurrences,
        int emittedOccurrences,
        Instant lastScheduledAt,
        Instant nextScheduledAt,
        UUID latestScheduledMessageId,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
