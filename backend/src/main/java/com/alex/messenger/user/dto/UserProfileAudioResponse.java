package com.alex.messenger.user.dto;

import java.time.Instant;
import java.util.UUID;

public record UserProfileAudioResponse(
        UUID userId,
        UUID attachmentId,
        String title,
        String performer,
        String caption,
        ProfileAudioAttachmentResponse attachment,
        Instant updatedAt
) {
}
