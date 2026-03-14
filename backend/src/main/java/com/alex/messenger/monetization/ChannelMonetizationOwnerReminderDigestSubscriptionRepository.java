package com.alex.messenger.monetization;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChannelMonetizationOwnerReminderDigestSubscriptionRepository
        extends JpaRepository<ChannelMonetizationOwnerReminderDigestSubscriptionEntity, UUID> {

    List<ChannelMonetizationOwnerReminderDigestSubscriptionEntity> findAllByChannelChatIdOrderByUpdatedAtDesc(UUID channelChatId);

    List<ChannelMonetizationOwnerReminderDigestSubscriptionEntity> findAllByChannelChatIdAndOwnerUserIdOrderByCreatedAtDesc(
            UUID channelChatId,
            UUID ownerUserId
    );

    Optional<ChannelMonetizationOwnerReminderDigestSubscriptionEntity> findByIdAndChannelChatIdAndOwnerUserId(
            UUID id,
            UUID channelChatId,
            UUID ownerUserId
    );

    @Query(value = """
        SELECT *
        FROM channel_monetization_owner_reminder_digest_subscriptions
        WHERE status = 'ACTIVE'
          AND (
                next_retry_at IS NOT NULL
                AND next_retry_at <= :eligibleBefore
              OR (
                next_retry_at IS NULL
                AND COALESCE(last_processed_at, created_at) + (min_interval_minutes || ' minutes')::interval <= :eligibleBefore
              )
          )
        ORDER BY COALESCE(next_retry_at, COALESCE(last_processed_at, created_at)) ASC, updated_at ASC
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<ChannelMonetizationOwnerReminderDigestSubscriptionEntity> lockDueBatch(
            @Param("eligibleBefore") Instant eligibleBefore,
            @Param("batchSize") int batchSize
    );
}
