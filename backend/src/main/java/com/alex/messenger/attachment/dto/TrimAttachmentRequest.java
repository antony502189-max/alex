package com.alex.messenger.attachment.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record TrimAttachmentRequest(
        @PositiveOrZero Long startMs,
        @Positive Long endMs
) {
    @JsonIgnore
    @AssertTrue(message = "Trim start and end are required")
    public boolean hasTrimWindow() {
        return startMs != null && endMs != null;
    }
}
