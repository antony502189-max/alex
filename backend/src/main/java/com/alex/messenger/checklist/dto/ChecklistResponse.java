package com.alex.messenger.checklist.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChecklistResponse(
        UUID checklistId,
        UUID chatId,
        UUID topicId,
        String title,
        String description,
        boolean archived,
        UUID createdByUserId,
        Instant createdAt,
        Instant updatedAt,
        int taskCount,
        int completedTaskCount,
        List<ChecklistTaskResponse> tasks
) {
}
