package com.alex.messenger.checklist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateChecklistTaskRequest(
        @NotBlank
        @Size(max = 500)
        String text,
        UUID assignedUserId,
        @PositiveOrZero
        Integer position
) {
}
