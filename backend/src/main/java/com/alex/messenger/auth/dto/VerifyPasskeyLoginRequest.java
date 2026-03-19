package com.alex.messenger.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record VerifyPasskeyLoginRequest(
        @NotNull UUID challengeId,
        @NotBlank @Size(max = 512) String challenge,
        @NotBlank @Size(max = 255) String credentialId,
        @PositiveOrZero Long signCount,
        @Size(max = 120) String deviceName,
        @Size(max = 32) String platform,
        @Size(max = 32) String appVersion
) {
}
