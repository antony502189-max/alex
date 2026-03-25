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
@Table(name = "compliance_case_export_download_audits")
public class ComplianceCaseExportDownloadAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "artifact_id", nullable = false)
    private UUID artifactId;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "operator_id", nullable = false, length = 120)
    private String operatorId;

    @Column(name = "downloaded_at", nullable = false)
    private Instant downloadedAt;

    @Column(name = "checksum_verified", nullable = false)
    private boolean checksumVerified;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = downloadedAt != null ? downloadedAt : Instant.now();
        }
        if (downloadedAt == null) {
            downloadedAt = createdAt;
        }
    }
}
