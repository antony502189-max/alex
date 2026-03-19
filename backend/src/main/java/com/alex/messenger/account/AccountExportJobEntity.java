package com.alex.messenger.account;

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
@Table(name = "account_export_jobs")
public class AccountExportJobEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "requested_by_session_id")
    private UUID requestedBySessionId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "export_format", nullable = false, length = 16)
    private String exportFormat;

    @Column(name = "include_attachments_metadata", nullable = false)
    private Boolean includeAttachmentsMetadata;

    @Column(name = "message_count", nullable = false)
    private Integer messageCount;

    @Column(name = "artifact_checksum", length = 128)
    private String artifactChecksum;

    @Column(name = "artifact_location", length = 512)
    private String artifactLocation;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = "PENDING";
        }
        if (includeAttachmentsMetadata == null) {
            includeAttachmentsMetadata = false;
        }
        if (messageCount == null) {
            messageCount = 0;
        }
    }
}
