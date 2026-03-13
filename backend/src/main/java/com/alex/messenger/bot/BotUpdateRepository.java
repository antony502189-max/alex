package com.alex.messenger.bot;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BotUpdateRepository extends JpaRepository<BotUpdateEntity, Long> {

    @Query(value = """
        SELECT *
        FROM bot_updates
        WHERE bot_user_id = :botUserId
          AND delivered_at IS NULL
          AND id > :offset
        ORDER BY id ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<BotUpdateEntity> findPendingLongPollUpdates(
            @Param("botUserId") UUID botUserId,
            @Param("offset") long offset,
            @Param("limit") int limit
    );

    @Query(value = """
        SELECT bu.*
        FROM bot_updates bu
        JOIN bot_accounts ba ON ba.bot_user_id = bu.bot_user_id
        WHERE bu.delivered_at IS NULL
          AND ba.webhook_enabled = TRUE
          AND ba.webhook_url IS NOT NULL
        ORDER BY bu.id ASC
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<BotUpdateEntity> lockWebhookDeliveryBatch(@Param("batchSize") int batchSize);
}
