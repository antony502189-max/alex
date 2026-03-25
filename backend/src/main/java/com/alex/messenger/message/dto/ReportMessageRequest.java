package com.alex.messenger.message.dto;

import jakarta.validation.constraints.Size;

public record ReportMessageRequest(
        @Size(max = 32) String category,
        @Size(max = 1000) String details
) {
}
