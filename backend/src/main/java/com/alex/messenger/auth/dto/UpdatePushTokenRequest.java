package com.alex.messenger.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdatePushTokenRequest(
        @NotBlank @Size(max = 16) @Pattern(regexp = "(?i)EXPO") String provider,
        @NotBlank @Size(max = 255) String pushToken
) {
}
