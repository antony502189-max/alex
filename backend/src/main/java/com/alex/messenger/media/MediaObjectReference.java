package com.alex.messenger.media;

public record MediaObjectReference(
        String bucketName,
        String objectKey,
        String storagePath
) {
}
