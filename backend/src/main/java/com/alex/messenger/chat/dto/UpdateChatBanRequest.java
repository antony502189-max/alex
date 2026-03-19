package com.alex.messenger.chat.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record UpdateChatBanRequest(
        @Future Instant bannedUntil,
        @Size(max = 255) String reason
) {
}
