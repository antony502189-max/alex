package com.alex.messenger.compliance;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComplianceCaseEventRepository extends JpaRepository<ComplianceCaseEventEntity, UUID> {

    List<ComplianceCaseEventEntity> findAllByCaseIdOrderByCreatedAtAsc(UUID caseId);
}
