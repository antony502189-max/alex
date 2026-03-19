package com.alex.messenger.checklist.dto;

import jakarta.validation.constraints.Size;

public record UpdateChecklistRequest(
        @Size(max = 120)
        String title,
        @Size(max = 1000)
        String description,
        Boolean archived
) {
}
