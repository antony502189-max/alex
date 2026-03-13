package com.alex.messenger.business.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BusinessChatTagPayload(
        @NotBlank @Size(max = 64) String tagName,
        @Size(max = 16) String color
) {
}
