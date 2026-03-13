package com.alex.messenger.attachment;

public record DownloadedAttachment(
        String originalFileName,
        String contentType,
        byte[] bytes
) {
}
