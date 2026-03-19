package com.alex.messenger.user.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ContactNoteResponse(
        UUID contactUserId,
        String note,
        LocalDate birthday,
        Instant updatedAt
) {
}
