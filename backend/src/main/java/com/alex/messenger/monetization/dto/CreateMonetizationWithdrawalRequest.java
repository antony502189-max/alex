package com.alex.messenger.monetization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateMonetizationWithdrawalRequest(
        @NotNull Long amountUnits,
        @NotBlank @Size(max = 32) String destinationType,
        @NotBlank @Size(max = 255) String destinationLabel,
        @Size(max = 255) String note
) {
}
