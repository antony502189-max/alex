package com.alex.messenger.secret;

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
@Table(name = "secret_chat_attachments")
public class SecretAttachmentEntity {

    @Id
    private UUID id;

    @Column(name = "secret_chat_id", nullable = false)
    private UUID secretChatId;

    @Column(name = "secret_message_id")
    private UUID secretMessageId;

    @Column(name = "uploader_user_id", nullable = false)
    private UUID uploaderUserId;

    @Column(name = "kind", nullable = false, length = 16)
    private String kind;

    @Column(name = "content_type", nullable = false, length = 255)
    private String contentType;

    @Column(name = "encrypted_file_size_bytes", nullable = false)
    private long encryptedFileSizeBytes;

    @Column(name = "storage_path", nullable = false, length = 512, unique = true)
    private String storagePath;

    @Column(name = "storage_provider", nullable = false, length = 32)
    private String storageProvider;

    @Column(name = "bucket_name", length = 255)
    private String bucketName;

    @Column(name = "object_key", length = 512)
    private String objectKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (storageProvider == null) {
            storageProvider = "S3";
        }
        if (kind == null) {
            kind = "FILE";
        }
    }
}
