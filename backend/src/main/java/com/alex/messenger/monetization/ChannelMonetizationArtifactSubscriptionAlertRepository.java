package com.alex.messenger.monetization;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChannelMonetizationArtifactSubscriptionAlertRepository
        extends JpaRepository<ChannelMonetizationArtifactSubscriptionAlertEntity, UUID> {

    List<ChannelMonetizationArtifactSubscriptionAlertEntity> findAllBySubscriptionIdOrderByCreatedAtDesc(UUID subscriptionId);

    List<ChannelMonetizationArtifactSubscriptionAlertEntity> findAllByChannelChatIdOrderByCreatedAtDesc(UUID channelChatId);

    List<ChannelMonetizationArtifactSubscriptionAlertEntity> findAllByChannelChatIdAndStatusOrderByCreatedAtDesc(
            UUID channelChatId,
            String status
    );

    Optional<ChannelMonetizationArtifactSubscriptionAlertEntity> findFirstBySubscriptionIdAndStatusOrderByCreatedAtDesc(
            UUID subscriptionId,
            String status
    );

    Optional<ChannelMonetizationArtifactSubscriptionAlertEntity> findFirstBySubscriptionIdOrderByCreatedAtDesc(UUID subscriptionId);

    Optional<ChannelMonetizationArtifactSubscriptionAlertEntity> findByIdAndSubscriptionId(UUID id, UUID subscriptionId);

    @Query(value = """
        SELECT *
        FROM channel_monetization_artifact_subscription_alerts
        WHERE status IN ('OPEN', 'ACKNOWLEDGED', 'SNOOZED')
          AND (
                acknowledge_by_due_at <= :dueBefore
                OR resolve_by_due_at <= :dueBefore
              )
        ORDER BY created_at ASC
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<ChannelMonetizationArtifactSubscriptionAlertEntity> lockDueReminderBatch(
            @Param("dueBefore") java.time.Instant dueBefore,
            @Param("batchSize") int batchSize
    );

    @Query(value = """
        SELECT *
        FROM channel_monetization_artifact_subscription_alerts
        WHERE severity = 'HIGH'
          AND owner_user_id IS NULL
          AND status IN ('OPEN', 'ACKNOWLEDGED', 'SNOOZED')
          AND triaged_at IS NULL
          AND COALESCE(severity_escalated_at, created_at) <= :dueBefore
        ORDER BY created_at ASC
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<ChannelMonetizationArtifactSubscriptionAlertEntity> lockPendingTriageBatch(
            @Param("dueBefore") java.time.Instant dueBefore,
            @Param("batchSize") int batchSize
    );

    @Query(value = """
        SELECT *
        FROM channel_monetization_artifact_subscription_alerts
        WHERE owner_user_id IS NULL
          AND triaged_at IS NOT NULL
          AND status IN ('OPEN', 'ACKNOWLEDGED', 'SNOOZED')
          AND COALESCE(triage_escalated_at, triaged_at) <= :dueBefore
        ORDER BY triaged_at ASC
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<ChannelMonetizationArtifactSubscriptionAlertEntity> lockDueTriageReminderBatch(
            @Param("dueBefore") java.time.Instant dueBefore,
            @Param("batchSize") int batchSize
    );
}
