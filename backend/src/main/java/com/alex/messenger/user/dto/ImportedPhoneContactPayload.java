package com.alex.messenger.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ImportedPhoneContactPayload(
        @NotBlank @Size(max = 32) String phoneNumber,
        @Size(max = 120) String contactName
) {
}
