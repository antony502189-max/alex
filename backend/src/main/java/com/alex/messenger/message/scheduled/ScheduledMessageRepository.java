package com.alex.messenger.message.scheduled;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduledMessageRepository extends JpaRepository<ScheduledMessageEntity, UUID> {

    List<ScheduledMessageEntity> findAllBySenderIdAndChatIdAndStatusInOrderByScheduledAtAsc(
            UUID senderId,
            UUID chatId,
            List<String> statuses
    );

    Optional<ScheduledMessageEntity> findByIdAndSenderId(UUID id, UUID senderId);

    Optional<ScheduledMessageEntity> findBySenderIdAndClientMessageId(UUID senderId, UUID clientMessageId);

    @Query(value = """
        SELECT *
        FROM scheduled_messages
        WHERE status = 'PENDING' AND scheduled_at <= :now
        ORDER BY scheduled_at ASC
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<ScheduledMessageEntity> lockDuePendingMessages(
            @Param("now") Instant now,
            @Param("batchSize") int batchSize
    );

    @Query(value = """
        SELECT *
        FROM scheduled_messages
        WHERE status = 'WAITING_ONLINE'
        ORDER BY created_at ASC
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<ScheduledMessageEntity> lockWaitingForOnlineMessages(@Param("batchSize") int batchSize);
}
