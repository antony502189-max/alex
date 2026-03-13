package com.alex.messenger.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ToggleReactionRequest(
        @NotBlank @Size(max = 32) String emoji
) {
}
