package com.alex.messenger.compliance;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComplianceCaseRepository extends JpaRepository<ComplianceCaseEntity, UUID> {
}
