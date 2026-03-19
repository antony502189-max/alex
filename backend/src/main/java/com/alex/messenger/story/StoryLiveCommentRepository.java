package com.alex.messenger.story;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoryLiveCommentRepository extends JpaRepository<StoryLiveCommentEntity, UUID> {

    List<StoryLiveCommentEntity> findAllByLiveSessionIdOrderByCreatedAtAsc(UUID liveSessionId);

    @Query(value = """
        SELECT slc.*
        FROM story_live_comments slc
        JOIN story_live_sessions sls ON sls.id = slc.live_session_id
        WHERE slc.donation_amount_minor IS NOT NULL
          AND slc.hook_delivered_at IS NULL
          AND slc.hook_delivery_attempts < :maxAttempts
          AND sls.donations_enabled = TRUE
          AND sls.donation_event_hook_url IS NOT NULL
          AND BTRIM(sls.donation_event_hook_url) <> ''
        ORDER BY slc.created_at ASC
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<StoryLiveCommentEntity> lockDonationHookDeliveryBatch(
            @Param("batchSize") int batchSize,
            @Param("maxAttempts") int maxAttempts
    );
}
