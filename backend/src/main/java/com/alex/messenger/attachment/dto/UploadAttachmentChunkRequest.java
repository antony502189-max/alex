package com.alex.messenger.attachment.dto;

public record UploadAttachmentChunkRequest(
        long offset,
        String base64Chunk
) {
}
