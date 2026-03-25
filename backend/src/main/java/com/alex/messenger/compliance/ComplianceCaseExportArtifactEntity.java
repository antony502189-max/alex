package com.alex.messenger.compliance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
@Table(name = "compliance_case_export_artifacts")
public class ComplianceCaseExportArtifactEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "exported_by_operator_id", nullable = false, length = 120)
    private String exportedByOperatorId;

    @Column(name = "exported_at", nullable = false)
    private Instant exportedAt;

    @Column(name = "message_count", nullable = false)
    private int messageCount;

    @Column(name = "artifact_checksum", nullable = false, length = 128)
    private String artifactChecksum;

    @Column(name = "storage_path", nullable = false, length = 512)
    private String storagePath;

    @Column(name = "encryption_iv", nullable = false, length = 64)
    private String encryptionIv;

    @Column(name = "content_type", nullable = false, length = 120)
    private String contentType;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "download_count", nullable = false)
    private int downloadCount;

    @Column(name = "last_downloaded_at")
    private Instant lastDownloadedAt;

    @Column(name = "last_downloaded_by_operator_id", length = 120)
    private String lastDownloadedByOperatorId;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (exportedAt == null) {
            exportedAt = createdAt;
        }
    }
}
