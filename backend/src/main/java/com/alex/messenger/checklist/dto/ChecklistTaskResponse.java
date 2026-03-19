package com.alex.messenger.checklist.dto;

import java.time.Instant;
import java.util.UUID;

public record ChecklistTaskResponse(
        UUID taskId,
        String text,
        int position,
        UUID assignedUserId,
        boolean completed,
        Instant completedAt,
        UUID completedByUserId,
        UUID createdByUserId,
        Instant createdAt,
        Instant updatedAt
) {
}
