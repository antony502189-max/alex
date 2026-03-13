package com.alex.messenger.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestLoginCodeRequest(
        @NotBlank @Size(max = 32) String phoneNumber,
        @Size(max = 120) String displayName,
        @Size(max = 120) String deviceName,
        @Size(max = 32) String platform,
        @Size(max = 32) String appVersion
) {
}
