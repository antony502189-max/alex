package com.alex.messenger.call.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CallSignalRequest(
        @NotNull UUID toUserId,
        @NotBlank
        @Size(max = 32)
        @Pattern(
                regexp = "(?i)^\\s*(offer|answer|candidate|ice[_-]?candidate|ringing|hangup|bye|renegotiate|restart[_-]?ice|media[_-]?state)\\s*$"
        )
        String signalType,
        @NotBlank @Size(max = 8000) String payload
) {
}
