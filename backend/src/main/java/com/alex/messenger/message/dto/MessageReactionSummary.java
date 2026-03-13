package com.alex.messenger.message.dto;

public record MessageReactionSummary(
        String emoji,
        long count
) {
}
