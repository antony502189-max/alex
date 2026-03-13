package com.alex.messenger.message.expiration;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageExpirationRepository extends JpaRepository<MessageExpirationEntity, UUID> {

    @Query(value = """
            SELECT *
            FROM message_expirations
            WHERE processed_at IS NULL
              AND expires_at <= :now
            ORDER BY expires_at ASC
            FOR UPDATE SKIP LOCKED
            LIMIT :limit
            """, nativeQuery = true)
    List<MessageExpirationEntity> lockDueExpirations(
            @Param("now") Instant now,
            @Param("limit") int limit
    );
}
