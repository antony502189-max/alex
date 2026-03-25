package com.alex.messenger.abuse;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AbuseActionEventRepository extends JpaRepository<AbuseActionEventEntity, UUID> {

    long countByActionTypeAndActorUserIdAndCreatedAtAfter(
            String actionType,
            UUID actorUserId,
            Instant createdAt
    );

    long countByActionTypeAndActorUserIdAndChatIdAndCreatedAtAfter(
            String actionType,
            UUID actorUserId,
            UUID chatId,
            Instant createdAt
    );
}
