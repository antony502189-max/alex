package com.alex.messenger.call.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record CreateCallJoinLinkRequest(
        @NotNull UUID chatId,
        @Pattern(regexp = "(?i)^\\s*(VOICE|VIDEO)\\s*$") String kind,
        @Pattern(regexp = "(?i)^\\s*(DIRECT|PRIVATE|GROUP|VOICE_CHAT|LIVE_STREAM)\\s*$") String mode,
        @Size(max = 120) String label,
        @Future Instant expiresAt
) {
}
