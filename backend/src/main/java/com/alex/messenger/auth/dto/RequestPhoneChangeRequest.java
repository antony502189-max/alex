package com.alex.messenger.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestPhoneChangeRequest(
        @NotBlank @Size(max = 32) String newPhoneNumber
) {
}
