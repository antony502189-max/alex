package com.alex.messenger.call.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record StartCallRequest(
        @NotNull UUID chatId,
        @Pattern(regexp = "VOICE|VIDEO") String kind,
        @Pattern(regexp = "DIRECT|PRIVATE|GROUP|VOICE_CHAT|LIVE_STREAM") String mode,
        Boolean recordingEnabled
) {
}
