package com.alex.messenger.checklist.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateChecklistRequest(
        @NotNull
        UUID chatId,
        UUID topicId,
        @NotBlank
        @Size(max = 120)
        String title,
        @Size(max = 1000)
        String description,
        List<@NotNull @Valid CreateChecklistTaskRequest> tasks
) {
}
