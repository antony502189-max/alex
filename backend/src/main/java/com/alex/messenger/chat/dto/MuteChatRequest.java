package com.alex.messenger.chat.dto;

import jakarta.validation.constraints.Future;
import java.time.Instant;

public record MuteChatRequest(
        @Future Instant mutedUntil
) {
}
