package com.alex.messenger.message.dto;

import jakarta.validation.constraints.Size;

public record DeleteMessageRequest(
        Boolean revokeForAll,
        @Size(max = 255) String adminReason
) {
}
