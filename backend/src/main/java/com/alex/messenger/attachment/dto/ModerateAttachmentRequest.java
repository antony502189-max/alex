package com.alex.messenger.attachment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ModerateAttachmentRequest(
        @NotBlank @Size(max = 16) String status,
        @Size(max = 255) String reason,
        Boolean sensitiveContent
) {
}
