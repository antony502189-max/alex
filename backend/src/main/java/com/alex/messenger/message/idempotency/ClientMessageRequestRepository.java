package com.alex.messenger.message.idempotency;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClientMessageRequestRepository extends JpaRepository<ClientMessageRequestEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select request
            from ClientMessageRequestEntity request
            where request.senderUserId = :senderUserId
              and request.clientMessageId = :clientMessageId
            """)
    Optional<ClientMessageRequestEntity> findLockedBySenderUserIdAndClientMessageId(
            @Param("senderUserId") UUID senderUserId,
            @Param("clientMessageId") UUID clientMessageId
    );

    @Modifying
    @Query(value = """
            insert into client_message_requests (
                id,
                sender_user_id,
                client_message_id,
                chat_id,
                message_id,
                status,
                created_at,
                updated_at
            )
            values (
                :id,
                :senderUserId,
                :clientMessageId,
                :chatId,
                :messageId,
                :status,
                :createdAt,
                :updatedAt
            )
            on conflict (sender_user_id, client_message_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("senderUserId") UUID senderUserId,
            @Param("clientMessageId") UUID clientMessageId,
            @Param("chatId") UUID chatId,
            @Param("messageId") UUID messageId,
            @Param("status") String status,
            @Param("createdAt") Instant createdAt,
            @Param("updatedAt") Instant updatedAt
    );
}
