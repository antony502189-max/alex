package com.alex.messenger.compliance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record ComplianceCaseCreateRequest(
        @NotBlank @Size(max = 120) String caseReference,
        @NotNull UUID targetUserId,
        @NotBlank @Size(max = 255) String legalBasis,
        @NotBlank @Size(max = 500) String reason,
        Instant fromInclusive,
        Instant toExclusive
) {
}
