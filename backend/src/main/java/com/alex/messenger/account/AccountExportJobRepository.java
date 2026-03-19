package com.alex.messenger.account;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountExportJobRepository extends JpaRepository<AccountExportJobEntity, UUID> {

    List<AccountExportJobEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
}
