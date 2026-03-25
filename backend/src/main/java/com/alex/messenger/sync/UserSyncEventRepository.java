package com.alex.messenger.sync;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

public interface UserSyncEventRepository extends JpaRepository<UserSyncEventEntity, Long> {

    List<UserSyncEventEntity> findTop201ByUserIdAndIdGreaterThanOrderByIdAsc(UUID userId, Long cursor);

    List<UserSyncEventEntity> findTop201ByUserIdOrderByIdAsc(UUID userId);

    UserSyncEventEntity findFirstByUserIdOrderByIdAsc(UUID userId);

    @Query("""
            select distinct event.chatId
            from UserSyncEventEntity event
            where event.chatId is not null
              and event.createdAt >= :createdAfter
              and event.eventType in :eventTypes
            """)
    List<UUID> findDistinctChatIdsForEventTypesCreatedAfter(
            @Param("createdAfter") Instant createdAfter,
            @Param("eventTypes") Collection<String> eventTypes,
            Pageable pageable
    );

    List<UserSyncEventEntity> findByCreatedAtBeforeOrderByCreatedAtAsc(Instant createdBefore, Pageable pageable);
}
