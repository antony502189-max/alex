package com.alex.messenger.monetization.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateMonetizationArtifactSubscriptionRequest(
        @NotNull UUID targetChatId,
        @NotBlank String artifactType,
        @Min(1) @Max(10080) Integer minIntervalMinutes,
        @Min(1) @Max(10080) Integer alertSuppressionMinutes,
        Boolean autoGenerate,
        String note
) {
}
