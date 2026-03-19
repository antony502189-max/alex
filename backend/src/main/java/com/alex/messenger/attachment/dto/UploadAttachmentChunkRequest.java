package com.alex.messenger.attachment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public record UploadAttachmentChunkRequest(
        @PositiveOrZero long offset,
        @NotBlank String base64Chunk
) {
}
