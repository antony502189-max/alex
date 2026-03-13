package com.alex.messenger.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QrLoginPollRequest(
        @NotBlank @Size(max = 512) String qrToken
) {
}
