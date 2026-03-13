package com.alex.messenger.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QrLoginBindRequest(
        @NotBlank @Size(max = 512) String qrToken,
        @Size(max = 120) String deviceName,
        @Size(max = 32) String platform,
        @Size(max = 32) String appVersion
) {
}
