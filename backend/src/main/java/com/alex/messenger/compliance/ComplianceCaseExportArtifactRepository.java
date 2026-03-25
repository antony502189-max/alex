package com.alex.messenger.compliance;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComplianceCaseExportArtifactRepository extends JpaRepository<ComplianceCaseExportArtifactEntity, UUID> {

    Optional<ComplianceCaseExportArtifactEntity> findFirstByCaseIdAndDeletedAtIsNullOrderByExportedAtDescCreatedAtDesc(UUID caseId);

    Optional<ComplianceCaseExportArtifactEntity> findByIdAndCaseId(UUID artifactId, UUID caseId);

    Optional<ComplianceCaseExportArtifactEntity> findByIdAndCaseIdAndDeletedAtIsNull(UUID artifactId, UUID caseId);

    List<ComplianceCaseExportArtifactEntity> findAllByCaseIdAndDeletedAtIsNullOrderByExportedAtDescCreatedAtDesc(UUID caseId);

    List<ComplianceCaseExportArtifactEntity> findByExpiresAtBeforeAndDeletedAtIsNullOrderByExpiresAtAsc(
            Instant now,
            Pageable pageable
    );
}
