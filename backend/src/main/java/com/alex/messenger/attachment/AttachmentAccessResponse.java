package com.alex.messenger.attachment;

import java.time.Instant;

public record AttachmentAccessResponse(
        String downloadUrl,
        String previewUrl,
        Instant accessExpiresAt,
        boolean requiresAuthorization
) {
}
