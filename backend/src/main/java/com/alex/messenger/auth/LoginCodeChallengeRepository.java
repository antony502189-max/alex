package com.alex.messenger.auth;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

public interface LoginCodeChallengeRepository extends JpaRepository<LoginCodeChallengeEntity, UUID> {

    long countByPhoneNumberAndCreatedAtAfter(String phoneNumber, Instant createdAt);

    long countByRequestedByIpAndCreatedAtAfter(String requestedByIp, Instant createdAt);

    long countByRequestFingerprintHashAndCreatedAtAfter(String requestFingerprintHash, Instant createdAt);

    Optional<LoginCodeChallengeEntity> findByIdAndConsumedAtIsNull(UUID id);

    @Query("""
            select challenge
            from LoginCodeChallengeEntity challenge
            where challenge.consumedAt is not null
                or challenge.expiresAt <= :cutoff
            order by challenge.createdAt asc
            """)
    List<LoginCodeChallengeEntity> findCleanupBatch(@Param("cutoff") Instant cutoff, Pageable pageable);
}
