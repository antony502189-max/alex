package com.alex.messenger.secret.dto;

import java.time.Instant;
import java.util.UUID;

public record SecretAttachmentUploadResponse(
        UUID attachmentId,
        String kind,
        long encryptedFileSizeBytes,
        Instant createdAt
) {
}
