package com.alex.messenger.monetization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record CreateSponsoredMessageRequest(
        UUID sponsorUserId,
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 1000) String messageText,
        @Size(max = 64) String callToActionLabel,
        @Size(max = 512) String callToActionUrl,
        @NotNull Long budgetUnits,
        Long costPerImpressionUnits,
        Long costPerClickUnits,
        Instant activeUntil
) {
}
