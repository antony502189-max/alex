package com.alex.messenger.compliance.dto;

import jakarta.validation.constraints.Size;

public record ComplianceCaseApproveRequest(
        @Size(max = 500) String approvalNote
) {
}
