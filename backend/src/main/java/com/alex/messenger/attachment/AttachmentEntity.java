package com.alex.messenger.attachment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name = "attachments")
public class AttachmentEntity {

    @Id
    private UUID id;

    @Column(name = "chat_id", nullable = false)
    private UUID chatId;

    @Column(name = "uploader_user_id", nullable = false)
    private UUID uploaderUserId;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Column(name = "content_type", nullable = false, length = 255)
    private String contentType;

    @Column(name = "kind", nullable = false, length = 16)
    private String kind;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "waveform", columnDefinition = "text")
    private String waveform;

    @Column(name = "album_id")
    private UUID albumId;

    @Column(name = "album_item_index")
    private Integer albumItemIndex;

    @Column(name = "source_attachment_id")
    private UUID sourceAttachmentId;

    @Column(name = "trim_start_ms")
    private Long trimStartMs;

    @Column(name = "trim_end_ms")
    private Long trimEndMs;

    @Column(name = "hd_photo", nullable = false)
    private boolean hdPhoto;

    @Column(name = "preview_bucket_name", length = 255)
    private String previewBucketName;

    @Column(name = "preview_object_key", length = 512)
    private String previewObjectKey;

    @Column(name = "thumbnail_bucket_name", length = 255)
    private String thumbnailBucketName;

    @Column(name = "thumbnail_object_key", length = 512)
    private String thumbnailObjectKey;

    @Column(name = "processing_status", nullable = false, length = 16)
    private String processingStatus;

    @Column(name = "moderation_status", nullable = false, length = 16)
    private String moderationStatus;

    @Column(name = "moderation_reason", length = 255)
    private String moderationReason;

    @Column(name = "moderation_sensitive", nullable = false)
    private boolean moderationSensitive;

    @Column(name = "moderation_reviewed_by_user_id")
    private UUID moderationReviewedByUserId;

    @Column(name = "moderation_reviewed_at")
    private Instant moderationReviewedAt;

    @Column(name = "storage_path", nullable = false, length = 512, unique = true)
    private String storagePath;

    @Column(name = "storage_provider", nullable = false, length = 32)
    private String storageProvider;

    @Column(name = "bucket_name", length = 255)
    private String bucketName;

    @Column(name = "object_key", length = 512)
    private String objectKey;

    @Column(name = "nonce", columnDefinition = "text")
    private String nonce;

    @Column(name = "key_version")
    private Integer keyVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (kind == null) {
            kind = "FILE";
        }
        if (storageProvider == null) {
            storageProvider = "LOCAL_FS";
        }
        if (processingStatus == null) {
            processingStatus = "NOT_REQUIRED";
        }
        if (moderationStatus == null) {
            moderationStatus = "APPROVED";
        }
    }

    public AttachmentEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getChatId() {
        return chatId;
    }

    public void setChatId(UUID chatId) {
        this.chatId = chatId;
    }

    public UUID getUploaderUserId() {
        return uploaderUserId;
    }

    public void setUploaderUserId(UUID uploaderUserId) {
        this.uploaderUserId = uploaderUserId;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

    public String getWaveform() {
        return waveform;
    }

    public void setWaveform(String waveform) {
        this.waveform = waveform;
    }

    public UUID getAlbumId() {
        return albumId;
    }

    public void setAlbumId(UUID albumId) {
        this.albumId = albumId;
    }

    public Integer getAlbumItemIndex() {
        return albumItemIndex;
    }

    public void setAlbumItemIndex(Integer albumItemIndex) {
        this.albumItemIndex = albumItemIndex;
    }

    public UUID getSourceAttachmentId() {
        return sourceAttachmentId;
    }

    public void setSourceAttachmentId(UUID sourceAttachmentId) {
        this.sourceAttachmentId = sourceAttachmentId;
    }

    public Long getTrimStartMs() {
        return trimStartMs;
    }

    public void setTrimStartMs(Long trimStartMs) {
        this.trimStartMs = trimStartMs;
    }

    public Long getTrimEndMs() {
        return trimEndMs;
    }

    public void setTrimEndMs(Long trimEndMs) {
        this.trimEndMs = trimEndMs;
    }

    public boolean isHdPhoto() {
        return hdPhoto;
    }

    public void setHdPhoto(boolean hdPhoto) {
        this.hdPhoto = hdPhoto;
    }

    public String getPreviewBucketName() {
        return previewBucketName;
    }

    public void setPreviewBucketName(String previewBucketName) {
        this.previewBucketName = previewBucketName;
    }

    public String getPreviewObjectKey() {
        return previewObjectKey;
    }

    public void setPreviewObjectKey(String previewObjectKey) {
        this.previewObjectKey = previewObjectKey;
    }

    public String getThumbnailBucketName() {
        return thumbnailBucketName;
    }

    public void setThumbnailBucketName(String thumbnailBucketName) {
        this.thumbnailBucketName = thumbnailBucketName;
    }

    public String getThumbnailObjectKey() {
        return thumbnailObjectKey;
    }

    public void setThumbnailObjectKey(String thumbnailObjectKey) {
        this.thumbnailObjectKey = thumbnailObjectKey;
    }

    public String getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(String processingStatus) {
        this.processingStatus = processingStatus;
    }

    public String getModerationStatus() {
        return moderationStatus;
    }

    public void setModerationStatus(String moderationStatus) {
        this.moderationStatus = moderationStatus;
    }

    public String getModerationReason() {
        return moderationReason;
    }

    public void setModerationReason(String moderationReason) {
        this.moderationReason = moderationReason;
    }

    public boolean isModerationSensitive() {
        return moderationSensitive;
    }

    public void setModerationSensitive(boolean moderationSensitive) {
        this.moderationSensitive = moderationSensitive;
    }

    public UUID getModerationReviewedByUserId() {
        return moderationReviewedByUserId;
    }

    public void setModerationReviewedByUserId(UUID moderationReviewedByUserId) {
        this.moderationReviewedByUserId = moderationReviewedByUserId;
    }

    public Instant getModerationReviewedAt() {
        return moderationReviewedAt;
    }

    public void setModerationReviewedAt(Instant moderationReviewedAt) {
        this.moderationReviewedAt = moderationReviewedAt;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public String getStorageProvider() {
        return storageProvider;
    }

    public void setStorageProvider(String storageProvider) {
        this.storageProvider = storageProvider;
    }

    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public String getNonce() {
        return nonce;
    }

    public void setNonce(String nonce) {
        this.nonce = nonce;
    }

    public Integer getKeyVersion() {
        return keyVersion;
    }

    public void setKeyVersion(Integer keyVersion) {
        this.keyVersion = keyVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
