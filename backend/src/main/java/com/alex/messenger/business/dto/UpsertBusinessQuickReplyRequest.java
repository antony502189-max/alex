package com.alex.messenger.business.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpsertBusinessQuickReplyRequest(
        @NotBlank @Size(max = 64) String shortcut,
        @NotBlank @Size(max = 1000) String messageText,
        Integer position
) {
}
