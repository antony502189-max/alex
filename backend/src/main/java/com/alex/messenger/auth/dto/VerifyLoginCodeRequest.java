package com.alex.messenger.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record VerifyLoginCodeRequest(
        @NotNull UUID challengeId,
        @NotBlank @Size(min = 4, max = 16) String code
) {
}
