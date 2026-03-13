package com.alex.messenger.lawful.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record DirectLawfulExportRequest(
        @NotNull UUID targetUserId,
        Instant fromInclusive,
        Instant toExclusive,
        @NotBlank @Size(max = 500) String reason,
        Boolean includeAttachmentsMetadata
) {
}
