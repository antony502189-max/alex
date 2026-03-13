package com.alex.messenger.lawful;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LawfulDirectExportRepository extends JpaRepository<LawfulDirectExportEntity, UUID> {
}
