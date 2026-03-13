package com.alex.messenger.chat.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record MarkReadRequest(
        @NotNull UUID messageId
) {
}
