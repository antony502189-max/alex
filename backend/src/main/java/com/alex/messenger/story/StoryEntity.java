package com.alex.messenger.story;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "stories")
public class StoryEntity {

    @Id
    private UUID id;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(name = "owner_chat_id")
    private UUID ownerChatId;

    @Column(name = "text", length = 500)
    private String text;

    @Column(name = "background_from", nullable = false, length = 16)
    private String backgroundFrom;

    @Column(name = "background_to", nullable = false, length = 16)
    private String backgroundTo;

    @Column(name = "text_color", nullable = false, length = 16)
    private String textColor;

    @Column(name = "media_kind", length = 16)
    private String mediaKind;

    @Column(name = "media_file_name", length = 255)
    private String mediaFileName;

    @Column(name = "media_content_type", length = 255)
    private String mediaContentType;

    @Column(name = "media_duration_ms")
    private Long mediaDurationMs;

    @Column(name = "media_storage_provider", length = 32)
    private String mediaStorageProvider;

    @Column(name = "media_bucket_name", length = 255)
    private String mediaBucketName;

    @Column(name = "media_object_key", length = 512)
    private String mediaObjectKey;

    @Column(name = "media_preview_bucket_name", length = 255)
    private String mediaPreviewBucketName;

    @Column(name = "media_preview_object_key", length = 512)
    private String mediaPreviewObjectKey;

    @Column(name = "media_processing_status", nullable = false, length = 16)
    private String mediaProcessingStatus;

    @Column(name = "audience", nullable = false, length = 16)
    private String audience;

    @Column(name = "allowed_viewer_user_ids", nullable = false, columnDefinition = "text")
    private String allowedViewerUserIds;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (audience == null) {
            audience = "DEFAULT";
        }
        if (allowedViewerUserIds == null) {
            allowedViewerUserIds = "";
        }
        if (mediaProcessingStatus == null) {
            mediaProcessingStatus = "NOT_REQUIRED";
        }
    }
}
