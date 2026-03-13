package com.alex.messenger.chat.dto;

import java.time.Instant;

public record MuteChatRequest(
        Instant mutedUntil
) {
}
