package com.alex.messenger.account;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountDeletionJobRepository extends JpaRepository<AccountDeletionJobEntity, UUID> {

    Optional<AccountDeletionJobEntity> findFirstByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, String status);

    List<AccountDeletionJobEntity> findAllByStatusAndScheduledForBeforeOrderByScheduledForAsc(
            String status,
            Instant scheduledFor,
            Pageable pageable
    );
}
