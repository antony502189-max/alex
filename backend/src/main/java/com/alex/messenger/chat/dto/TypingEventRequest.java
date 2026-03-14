package com.alex.messenger.chat.dto;

import java.util.UUID;

public record TypingEventRequest(
        boolean typing,
        UUID topicId
) {
}
