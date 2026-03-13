package com.alex.messenger.message.dto;

import jakarta.validation.constraints.Size;

public record MessageServicePayload(
        @Size(max = 64) String serviceType,
        @Size(max = 512) String text
) {
}
