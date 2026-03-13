package com.alex.messenger.media;

import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProfilePhotoService {

    private static final Logger log = LoggerFactory.getLogger(ProfilePhotoService.class);

    private final MediaService mediaService;
    private final long maxFileSizeBytes;

    public ProfilePhotoService(
            MediaService mediaService,
            @Value("${alex.storage.profile-photos.max-file-size-bytes}") long maxFileSizeBytes
    ) {
        this.mediaService = mediaService;
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public StoredPhotoReference uploadUserPhoto(UUID userId, MultipartFile file) {
        validatePhoto(file);
        try (var inputStream = file.getInputStream()) {
            MediaObjectReference reference = mediaService.uploadUserPhoto(
                    userId,
                    safeFileName(file.getOriginalFilename()),
                    normalizeContentType(file.getContentType()),
                    file.getSize(),
                    inputStream
            );
            return new StoredPhotoReference(
                    "S3",
                    reference.bucketName(),
                    reference.objectKey(),
                    normalizeContentType(file.getContentType())
            );
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read profile photo", exception);
        }
    }

    public StoredPhotoReference uploadChatPhoto(UUID chatId, MultipartFile file) {
        validatePhoto(file);
        try (var inputStream = file.getInputStream()) {
            MediaObjectReference reference = mediaService.uploadChatPhoto(
                    chatId,
                    safeFileName(file.getOriginalFilename()),
                    normalizeContentType(file.getContentType()),
                    file.getSize(),
                    inputStream
            );
            return new StoredPhotoReference(
                    "S3",
                    reference.bucketName(),
                    reference.objectKey(),
                    normalizeContentType(file.getContentType())
            );
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read chat photo", exception);
        }
    }

    public PhotoAccess buildPhotoAccess(String storageProvider, String bucketName, String objectKey) {
        if (!"S3".equalsIgnoreCase(storageProvider) || bucketName == null || objectKey == null) {
            return new PhotoAccess(null, null);
        }
        PresignedMediaAccess access = mediaService.buildDownloadAccess(bucketName, objectKey);
        return new PhotoAccess(access.downloadUrl(), access.expiresAt());
    }

    public void deletePhoto(String storageProvider, String bucketName, String objectKey) {
        if (!"S3".equalsIgnoreCase(storageProvider) || bucketName == null || objectKey == null) {
            return;
        }
        try {
            mediaService.deleteObject(bucketName, objectKey);
        } catch (RuntimeException cleanupFailure) {
            log.warn("Unable to delete obsolete photo object s3://{}/{}", bucketName, objectKey, cleanupFailure);
        }
    }

    private void validatePhoto(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Profile photo is empty");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Profile photo is too large");
        }
        String contentType = normalizeContentType(file.getContentType());
        if (!contentType.startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Profile photo must be an image");
        }
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "application/octet-stream";
        }
        return contentType.trim().toLowerCase();
    }

    private String safeFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return "photo";
        }
        String normalized = originalFileName.replace("\\", "_").replace("/", "_").trim();
        return normalized.length() > 255 ? normalized.substring(0, 255) : normalized;
    }
}
