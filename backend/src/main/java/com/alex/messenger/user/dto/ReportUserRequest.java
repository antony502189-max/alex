package com.alex.messenger.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ReportUserRequest(
        @NotNull UUID reportedUserId,
        @Size(min = 3, max = 32) String category,
        @Size(max = 1000) String details
) {
}
