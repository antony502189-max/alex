package com.alex.messenger.media;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;

@Service
public class MediaService {

    private static final int MAX_PRESIGNED_URL_SECONDS = 7 * 24 * 60 * 60;

    private final MinioClient mediaStorageMinioClient;

    @Qualifier("mediaPresignMinioClient")
    private final MinioClient mediaPresignMinioClient;

    @Value("${alex.media.s3.bucket}")
    private String bucketName;

    @Value("${alex.media.s3.presigned-url-ttl}")
    private Duration presignedUrlTtl;

    public MediaService(
            MinioClient mediaStorageMinioClient,
            @Qualifier("mediaPresignMinioClient") MinioClient mediaPresignMinioClient
    ) {
        this.mediaStorageMinioClient = mediaStorageMinioClient;
        this.mediaPresignMinioClient = mediaPresignMinioClient;
    }

    @PostConstruct
    void ensureBucketExists() {
        try {
            boolean exists = mediaStorageMinioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build()
            );
            if (!exists) {
                mediaStorageMinioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to initialize media bucket", exception);
        }
    }

    public MediaObjectReference upload(
            UUID chatId,
            UUID attachmentId,
            String originalFileName,
            String contentType,
            long fileSizeBytes,
            InputStream inputStream
    ) {
        String objectKey = buildObjectKey(chatId, attachmentId, originalFileName);
        return uploadToObjectKey(objectKey, contentType, fileSizeBytes, inputStream);
    }

    public MediaObjectReference uploadUserPhoto(
            UUID userId,
            String originalFileName,
            String contentType,
            long fileSizeBytes,
            InputStream inputStream
    ) {
        String objectKey = buildUserPhotoObjectKey(userId, originalFileName);
        return uploadToObjectKey(objectKey, contentType, fileSizeBytes, inputStream);
    }

    public MediaObjectReference uploadChatPhoto(
            UUID chatId,
            String originalFileName,
            String contentType,
            long fileSizeBytes,
            InputStream inputStream
    ) {
        String objectKey = buildChatPhotoObjectKey(chatId, originalFileName);
        return uploadToObjectKey(objectKey, contentType, fileSizeBytes, inputStream);
    }

    public MediaObjectReference uploadStoryMedia(
            UUID userId,
            UUID storyId,
            String originalFileName,
            String contentType,
            long fileSizeBytes,
            InputStream inputStream
    ) {
        String objectKey = buildStoryMediaObjectKey(userId, storyId, originalFileName);
        return uploadToObjectKey(objectKey, contentType, fileSizeBytes, inputStream);
    }

    public MediaObjectReference uploadSecretAttachment(
            UUID secretChatId,
            UUID attachmentId,
            String originalFileName,
            String contentType,
            long fileSizeBytes,
            InputStream inputStream
    ) {
        String objectKey = buildSecretAttachmentObjectKey(secretChatId, attachmentId, originalFileName);
        return uploadToObjectKey(objectKey, contentType, fileSizeBytes, inputStream);
    }

    public void deleteObject(String bucketName, String objectKey) {
        try {
            mediaStorageMinioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to delete media", exception);
        }
    }

    public byte[] downloadObjectBytes(String bucketName, String objectKey) {
        try (InputStream inputStream = mediaStorageMinioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectKey)
                        .build()
        )) {
            return inputStream.readAllBytes();
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to load media", exception);
        }
    }

    public MediaObjectReference uploadAttachmentPreview(
            UUID chatId,
            UUID attachmentId,
            String originalFileName,
            long fileSizeBytes,
            InputStream inputStream
    ) {
        String objectKey = buildAttachmentPreviewObjectKey(chatId, attachmentId, originalFileName);
        return uploadToObjectKey(objectKey, "image/jpeg", fileSizeBytes, inputStream);
    }

    public MediaObjectReference uploadAttachmentThumbnail(
            UUID chatId,
            UUID attachmentId,
            String originalFileName,
            long fileSizeBytes,
            InputStream inputStream
    ) {
        String objectKey = buildAttachmentThumbnailObjectKey(chatId, attachmentId, originalFileName);
        return uploadToObjectKey(objectKey, "image/jpeg", fileSizeBytes, inputStream);
    }

    public MediaObjectReference uploadStoryPreview(
            UUID userId,
            UUID storyId,
            String originalFileName,
            long fileSizeBytes,
            InputStream inputStream
    ) {
        String objectKey = buildStoryPreviewObjectKey(userId, storyId, originalFileName);
        return uploadToObjectKey(objectKey, "image/jpeg", fileSizeBytes, inputStream);
    }

    private MediaObjectReference uploadToObjectKey(
            String objectKey,
            String contentType,
            long fileSizeBytes,
            InputStream inputStream
    ) {
        try {
            mediaStorageMinioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .contentType(contentType)
                            .stream(inputStream, fileSizeBytes, -1)
                            .build()
            );
            return new MediaObjectReference(
                    bucketName,
                    objectKey,
                    "s3://" + bucketName + "/" + objectKey
            );
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to store media", exception);
        }
    }

    public PresignedMediaAccess buildDownloadAccess(String bucketName, String objectKey) {
        Instant expiresAt = Instant.now().plus(presignedUrlTtl);
        int expirySeconds = (int) Math.max(1, Math.min(presignedUrlTtl.toSeconds(), MAX_PRESIGNED_URL_SECONDS));
        try {
            String downloadUrl = mediaPresignMinioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(objectKey)
                            .expiry(expirySeconds, TimeUnit.SECONDS)
                            .build()
            );
            return new PresignedMediaAccess(downloadUrl, expiresAt);
        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to generate media access url",
                    exception
            );
        }
    }

    private String buildObjectKey(UUID chatId, UUID attachmentId, String originalFileName) {
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        String extension = extractExtension(originalFileName);
        return "chats/%s/%04d/%02d/%02d/%s%s".formatted(
                chatId,
                now.getYear(),
                now.getMonthValue(),
                now.getDayOfMonth(),
                attachmentId,
                extension
        );
    }

    private String buildAttachmentPreviewObjectKey(UUID chatId, UUID attachmentId, String originalFileName) {
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        return "chats/%s/previews/%04d/%02d/%02d/%s%s".formatted(
                chatId,
                now.getYear(),
                now.getMonthValue(),
                now.getDayOfMonth(),
                attachmentId,
                previewExtension(originalFileName)
        );
    }

    private String buildAttachmentThumbnailObjectKey(UUID chatId, UUID attachmentId, String originalFileName) {
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        return "chats/%s/thumbnails/%04d/%02d/%02d/%s%s".formatted(
                chatId,
                now.getYear(),
                now.getMonthValue(),
                now.getDayOfMonth(),
                attachmentId,
                previewExtension(originalFileName)
        );
    }

    private String buildUserPhotoObjectKey(UUID userId, String originalFileName) {
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        String extension = extractExtension(originalFileName);
        return "users/%s/profile/%04d/%02d/%02d/%s%s".formatted(
                userId,
                now.getYear(),
                now.getMonthValue(),
                now.getDayOfMonth(),
                UUID.randomUUID(),
                extension
        );
    }

    private String buildChatPhotoObjectKey(UUID chatId, String originalFileName) {
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        String extension = extractExtension(originalFileName);
        return "chats/%s/photo/%04d/%02d/%02d/%s%s".formatted(
                chatId,
                now.getYear(),
                now.getMonthValue(),
                now.getDayOfMonth(),
                UUID.randomUUID(),
                extension
        );
    }

    private String buildStoryMediaObjectKey(UUID userId, UUID storyId, String originalFileName) {
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        String extension = extractExtension(originalFileName);
        return "users/%s/stories/%04d/%02d/%02d/%s%s".formatted(
                userId,
                now.getYear(),
                now.getMonthValue(),
                now.getDayOfMonth(),
                storyId,
                extension
        );
    }

    private String buildStoryPreviewObjectKey(UUID userId, UUID storyId, String originalFileName) {
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        return "users/%s/stories/%04d/%02d/%02d/%s-preview%s".formatted(
                userId,
                now.getYear(),
                now.getMonthValue(),
                now.getDayOfMonth(),
                storyId,
                previewExtension(originalFileName)
        );
    }

    private String buildSecretAttachmentObjectKey(UUID secretChatId, UUID attachmentId, String originalFileName) {
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        String extension = extractExtension(originalFileName);
        return "secret-chats/%s/%04d/%02d/%02d/%s%s".formatted(
                secretChatId,
                now.getYear(),
                now.getMonthValue(),
                now.getDayOfMonth(),
                attachmentId,
                extension
        );
    }

    private String extractExtension(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return "";
        }
        int dotIndex = originalFileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == originalFileName.length() - 1) {
            return "";
        }
        String rawExtension = originalFileName.substring(dotIndex).toLowerCase(Locale.ROOT);
        return rawExtension.length() > 16 ? rawExtension.substring(0, 16) : rawExtension;
    }

    private String previewExtension(String originalFileName) {
        String extension = extractExtension(originalFileName);
        if (".jpeg".equals(extension) || ".jpg".equals(extension)) {
            return extension;
        }
        return ".jpg";
    }
}
