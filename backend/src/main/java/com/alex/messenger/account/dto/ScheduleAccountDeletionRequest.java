package com.alex.messenger.account.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record ScheduleAccountDeletionRequest(
        @Size(max = 255) String reason,
        @Min(1) @Max(365) Integer delayDays
) {
}
