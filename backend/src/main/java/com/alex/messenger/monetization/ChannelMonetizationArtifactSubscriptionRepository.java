package com.alex.messenger.monetization;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChannelMonetizationArtifactSubscriptionRepository
        extends JpaRepository<ChannelMonetizationArtifactSubscriptionEntity, UUID> {

    List<ChannelMonetizationArtifactSubscriptionEntity> findAllByChannelChatIdOrderByCreatedAtDesc(UUID channelChatId);

    Optional<ChannelMonetizationArtifactSubscriptionEntity> findByIdAndChannelChatId(UUID id, UUID channelChatId);

    @Query(value = """
        SELECT *
        FROM channel_monetization_artifact_subscriptions
        WHERE status = 'ACTIVE'
          AND COALESCE(last_delivered_at, created_at) <= :eligibleBefore
        ORDER BY updated_at ASC
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<ChannelMonetizationArtifactSubscriptionEntity> lockActiveBatch(
            @Param("eligibleBefore") Instant eligibleBefore,
            @Param("batchSize") int batchSize
    );

    @Query(value = """
        SELECT *
        FROM channel_monetization_artifact_subscriptions
        WHERE status = 'ACTIVE'
          AND escalation_status IN ('OPEN', 'SUPPRESSED', 'SNOOZED')
          AND last_failure_at <= :eligibleBefore
        ORDER BY updated_at ASC
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<ChannelMonetizationArtifactSubscriptionEntity> lockEscalatedBatch(
            @Param("eligibleBefore") Instant eligibleBefore,
            @Param("batchSize") int batchSize
    );
}
