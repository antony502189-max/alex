package com.alex.messenger.checklist.dto;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UpdateChecklistTaskRequest(
        @Size(max = 500)
        String text,
        Boolean completed,
        UUID assignedUserId,
        Boolean clearAssignee,
        @PositiveOrZero
        Integer position
) {
}
