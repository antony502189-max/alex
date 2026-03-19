package com.alex.messenger.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record VerifyPhoneChangeRequest(
        @NotNull UUID challengeId,
        @NotBlank @Size(max = 8) String code
) {
}
