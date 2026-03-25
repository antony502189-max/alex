package com.alex.messenger.chat.dto;

import jakarta.validation.constraints.Size;

public record ReportChatRequest(
        @Size(max = 32) String category,
        @Size(max = 1000) String details
) {
}
