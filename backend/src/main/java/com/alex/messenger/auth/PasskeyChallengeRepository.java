package com.alex.messenger.auth;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasskeyChallengeRepository extends JpaRepository<PasskeyChallengeEntity, UUID> {

    Optional<PasskeyChallengeEntity> findByIdAndConsumedAtIsNull(UUID id);

    @Query("""
        select challenge
        from PasskeyChallengeEntity challenge
        where challenge.expiresAt < :cutoff
           or challenge.consumedAt is not null
        order by challenge.createdAt asc
        """)
    List<PasskeyChallengeEntity> findCleanupBatch(@Param("cutoff") Instant cutoff, Pageable pageable);
}
