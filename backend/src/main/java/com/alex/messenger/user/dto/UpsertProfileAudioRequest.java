package com.alex.messenger.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UpsertProfileAudioRequest(
        UUID attachmentId,
        @Size(max = 120) String title,
        @Size(max = 120) String performer,
        @Size(max = 255) String caption
) {

    @JsonIgnore
    @AssertTrue(message = "Attachment is required when profile audio metadata is provided")
    public boolean hasAttachmentWhenMetadataProvided() {
        return attachmentId != null || isBlank(title) && isBlank(performer) && isBlank(caption);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isBlank();
    }
}
