package com.alex.messenger.auth.session;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSessionRepository extends JpaRepository<UserSessionEntity, UUID> {

    List<UserSessionEntity> findAllByUserIdAndRevokedAtIsNullOrderByLastActiveAtDesc(UUID userId);

    List<UserSessionEntity> findAllByUserIdAndRevokedAtIsNullAndNotificationsEnabledTrueAndPushTokenIsNotNull(UUID userId);

    Optional<UserSessionEntity> findByIdAndRevokedAtIsNull(UUID sessionId);

    Optional<UserSessionEntity> findByRefreshTokenHashAndRevokedAtIsNull(String refreshTokenHash);

    boolean existsByIdAndUserIdAndRevokedAtIsNull(UUID sessionId, UUID userId);

    boolean existsByUserIdAndRevokedAtIsNullAndLastActiveAtAfter(UUID userId, Instant threshold);

    @Modifying
    @Query("""
        update UserSessionEntity s
        set s.lastActiveAt = :now
        where s.id = :sessionId
          and s.userId = :userId
          and s.revokedAt is null
          and (s.lastActiveAt is null or s.lastActiveAt < :threshold)
        """)
    int touchIfStale(
            @Param("sessionId") UUID sessionId,
            @Param("userId") UUID userId,
            @Param("now") Instant now,
            @Param("threshold") Instant threshold
    );

    @Modifying
    @Query("""
        update UserSessionEntity s
        set s.revokedAt = :revokedAt
        where s.id = :sessionId
          and s.userId = :userId
          and s.revokedAt is null
        """)
    int revoke(
            @Param("sessionId") UUID sessionId,
            @Param("userId") UUID userId,
            @Param("revokedAt") Instant revokedAt
    );

    @Modifying
    @Query("""
        update UserSessionEntity s
        set s.revokedAt = :revokedAt
        where s.userId = :userId
          and s.id <> :currentSessionId
          and s.revokedAt is null
        """)
    int revokeOthers(
            @Param("userId") UUID userId,
            @Param("currentSessionId") UUID currentSessionId,
            @Param("revokedAt") Instant revokedAt
    );

    @Modifying
    @Query("""
        update UserSessionEntity s
        set s.revokedAt = :revokedAt
        where s.userId = :userId
          and s.revokedAt is null
        """)
    int revokeAllForUser(
            @Param("userId") UUID userId,
            @Param("revokedAt") Instant revokedAt
    );
}
