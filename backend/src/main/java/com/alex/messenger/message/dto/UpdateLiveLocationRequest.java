package com.alex.messenger.message.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateLiveLocationRequest(
        @NotNull
        @DecimalMin(value = "-90.0")
        @DecimalMax(value = "90.0")
        Double latitude,
        @NotNull
        @DecimalMin(value = "-180.0")
        @DecimalMax(value = "180.0")
        Double longitude,
        @Size(max = 120)
        String title,
        @Size(max = 240)
        String address
) {
}
