package com.alex.messenger.media;

public record StoredPhotoReference(
        String storageProvider,
        String bucketName,
        String objectKey,
        String contentType
) {
}
