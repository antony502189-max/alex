package com.alex.messenger.call.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record CreateCallJoinLinkRequest(
        @NotNull UUID chatId,
        @Pattern(regexp = "VOICE|VIDEO") String kind,
        @Pattern(regexp = "DIRECT|PRIVATE|GROUP|VOICE_CHAT|LIVE_STREAM") String mode,
        @Size(max = 120) String label,
        Instant expiresAt
) {
}
