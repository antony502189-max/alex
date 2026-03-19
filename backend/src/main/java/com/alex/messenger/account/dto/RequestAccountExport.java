package com.alex.messenger.account.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record RequestAccountExport(
        @Size(max = 16) String format,
        Boolean includeAttachmentsMetadata,
        Instant fromInclusive,
        Instant toExclusive
) {

    @JsonIgnore
    @AssertTrue(message = "Unsupported export format")
    public boolean hasSupportedFormat() {
        if (format == null || format.trim().isBlank()) {
            return true;
        }
        return "JSON".equalsIgnoreCase(format.trim());
    }

    @JsonIgnore
    @AssertTrue(message = "Export range is invalid")
    public boolean hasValidRange() {
        return fromInclusive == null || toExclusive == null || !fromInclusive.isAfter(toExclusive);
    }
}
