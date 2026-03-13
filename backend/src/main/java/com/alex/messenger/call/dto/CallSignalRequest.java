package com.alex.messenger.call.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CallSignalRequest(
        @NotNull UUID toUserId,
        @NotBlank @Size(max = 32) String signalType,
        @NotBlank @Size(max = 8000) String payload
) {
}
