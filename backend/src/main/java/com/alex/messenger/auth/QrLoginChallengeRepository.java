package com.alex.messenger.auth;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QrLoginChallengeRepository extends JpaRepository<QrLoginChallengeEntity, UUID> {

    Optional<QrLoginChallengeEntity> findByQrTokenHashAndConsumedAtIsNull(String qrTokenHash);

    Optional<QrLoginChallengeEntity> findByIdAndUserIdAndConsumedAtIsNull(UUID id, UUID userId);

    List<QrLoginChallengeEntity> findAllByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc(UUID userId);

    @Query("""
            select challenge
            from QrLoginChallengeEntity challenge
            where challenge.consumedAt is not null
                or challenge.declinedAt is not null
                or challenge.expiresAt <= :cutoff
            order by challenge.createdAt asc
            """)
    List<QrLoginChallengeEntity> findCleanupBatch(@Param("cutoff") Instant cutoff, Pageable pageable);
}
