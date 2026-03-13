package com.alex.messenger.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record VerifyTwoFactorRequest(
        @NotNull UUID challengeId,
        @NotBlank @Size(min = 8, max = 128) String password,
        Boolean trustSession
) {
}
