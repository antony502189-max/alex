package com.alex.messenger.compliance;

import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComplianceCaseExportDownloadAuditRepository
        extends JpaRepository<ComplianceCaseExportDownloadAuditEntity, UUID> {

    List<ComplianceCaseExportDownloadAuditEntity> findAllByCaseIdOrderByDownloadedAtDesc(UUID caseId);

    List<ComplianceCaseExportDownloadAuditEntity> findAllByCaseIdAndArtifactIdOrderByDownloadedAtDesc(
            UUID caseId,
            UUID artifactId
    );
}
