package com.alex.messenger.user.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpsertContactNoteRequest(
        @Size(max = 500) String note,
        LocalDate birthday
) {
}
