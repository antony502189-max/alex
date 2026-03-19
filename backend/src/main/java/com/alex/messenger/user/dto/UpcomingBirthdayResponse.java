package com.alex.messenger.user.dto;

import java.time.LocalDate;
import java.util.UUID;

public record UpcomingBirthdayResponse(
        UUID contactUserId,
        String contactName,
        String displayName,
        String username,
        LocalDate birthday,
        LocalDate nextOccurrence,
        int daysUntil
) {
}
