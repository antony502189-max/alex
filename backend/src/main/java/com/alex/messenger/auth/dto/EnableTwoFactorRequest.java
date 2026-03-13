package com.alex.messenger.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EnableTwoFactorRequest(
        @NotBlank @Size(min = 8, max = 128) String password,
        @Size(max = 120) String hint
) {
}
