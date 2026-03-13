package com.alex.messenger.compliance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "compliance_cases")
public class ComplianceCaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "target_user_id", nullable = false)
    private UUID targetUserId;

    @Column(name = "case_reference", nullable = false, length = 120)
    private String caseReference;

    @Column(name = "legal_basis", nullable = false, length = 255)
    private String legalBasis;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "from_inclusive")
    private Instant fromInclusive;

    @Column(name = "to_exclusive")
    private Instant toExclusive;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ComplianceCaseStatus status;

    @Column(name = "requested_by_operator_id", nullable = false, length = 120)
    private String requestedByOperatorId;

    @Column(name = "approved_by_operator_id", length = 120)
    private String approvedByOperatorId;

    @Column(name = "last_exported_by_operator_id", length = 120)
    private String lastExportedByOperatorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "last_exported_at")
    private Instant lastExportedAt;

    @Column(name = "export_count", nullable = false)
    private Integer exportCount;

    @Column(name = "latest_artifact_checksum", length = 128)
    private String latestArtifactChecksum;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = ComplianceCaseStatus.PENDING_APPROVAL;
        }
        if (exportCount == null) {
            exportCount = 0;
        }
    }
}
