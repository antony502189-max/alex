package com.alex.messenger.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record VerifyPasskeyRegistrationRequest(
        @NotNull UUID challengeId,
        @NotBlank @Size(max = 512) String challenge,
        @NotBlank @Size(max = 255) String credentialId,
        @NotBlank @Size(max = 8192) String publicKey,
        @Size(max = 255) String transports,
        @Size(max = 120) String label,
        Long signCount
) {
}
