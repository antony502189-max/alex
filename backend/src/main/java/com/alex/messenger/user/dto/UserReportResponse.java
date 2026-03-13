package com.alex.messenger.user.dto;

import java.time.Instant;
import java.util.UUID;

public record UserReportResponse(
        UUID reportId,
        UUID reportedUserId,
        String category,
        Instant createdAt
) {
}
