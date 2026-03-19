package com.alex.messenger.call.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCallReactionRequest(
        @NotBlank @Size(max = 64) String emoji
) {
}
