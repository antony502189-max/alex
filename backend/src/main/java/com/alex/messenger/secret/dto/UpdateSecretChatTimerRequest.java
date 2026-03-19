package com.alex.messenger.secret.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateSecretChatTimerRequest(
        @PositiveOrZero @Max(604_800) Integer autoDeleteSeconds
) {
}
