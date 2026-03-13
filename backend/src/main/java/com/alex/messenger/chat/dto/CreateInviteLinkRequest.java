package com.alex.messenger.chat.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateInviteLinkRequest(
        @Size(max = 120) String label,
        @Positive Integer usageLimit,
        Instant expiresAt
) {
}
