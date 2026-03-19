package com.alex.messenger.call;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CallSessionRepository extends JpaRepository<CallSessionEntity, UUID> {

    boolean existsByChatIdAndStatusIn(UUID chatId, Collection<String> statuses);

    CallSessionEntity findFirstByChatIdAndStatusInOrderByStartedAtDesc(UUID chatId, Collection<String> statuses);

    @Query("""
        select cs
        from CallSessionEntity cs
        join CallParticipantEntity cp on cp.id.callId = cs.id
        where cp.id.userId = :userId and cs.status in :statuses
        order by coalesce(cs.answeredAt, cs.startedAt) desc
        """)
    List<CallSessionEntity> findByParticipantAndStatuses(
            @Param("userId") UUID userId,
            @Param("statuses") Collection<String> statuses
    );

    @Query("""
        select cs
        from CallSessionEntity cs
        join CallParticipantEntity cp on cp.id.callId = cs.id
        where cp.id.userId = :userId
        order by coalesce(cs.endedAt, cs.answeredAt, cs.startedAt) desc
        """)
    List<CallSessionEntity> findRecentByParticipant(
            @Param("userId") UUID userId,
            Pageable pageable
    );

    List<CallSessionEntity> findAllByStatusAndStartedAtBeforeOrderByStartedAtAsc(
            String status,
            Instant startedAt,
            Pageable pageable
    );
}
