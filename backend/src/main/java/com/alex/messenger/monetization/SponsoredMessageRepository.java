package com.alex.messenger.monetization;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SponsoredMessageRepository extends JpaRepository<SponsoredMessageEntity, UUID> {

    List<SponsoredMessageEntity> findAllByChannelChatIdOrderByCreatedAtDesc(UUID channelChatId);

    @Query(value = """
        SELECT *
        FROM sponsored_messages
        WHERE status IN ('COMPLETED', 'CANCELED')
          AND earned_units > settled_units
          AND COALESCE(completed_at, canceled_at, updated_at) <= :eligibleBefore
        ORDER BY COALESCE(completed_at, canceled_at, updated_at) ASC, created_at ASC
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<SponsoredMessageEntity> lockReadyForPayoutBatch(
            @Param("eligibleBefore") java.time.Instant eligibleBefore,
            @Param("batchSize") int batchSize
    );

    @Query(value = """
        SELECT *
        FROM sponsored_messages
        WHERE channel_chat_id = :chatId
          AND status IN ('COMPLETED', 'CANCELED')
          AND earned_units > settled_units
        ORDER BY COALESCE(completed_at, canceled_at, updated_at) ASC, created_at ASC
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<SponsoredMessageEntity> lockReadyForPayoutByChannel(
            @Param("chatId") UUID chatId,
            @Param("batchSize") int batchSize
    );
}
