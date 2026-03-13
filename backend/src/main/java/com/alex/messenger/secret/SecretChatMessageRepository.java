package com.alex.messenger.secret;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SecretChatMessageRepository extends JpaRepository<SecretChatMessageEntity, UUID> {

    @Query("""
        select m
        from SecretChatMessageEntity m
        where m.secretChatId = :secretChatId
          and (m.expiresAt is null or m.expiresAt > :now)
        order by m.createdAt desc
        """)
    List<SecretChatMessageEntity> findRecentVisible(
            @Param("secretChatId") UUID secretChatId,
            @Param("now") Instant now,
            Pageable pageable
    );

    @Query("""
        select m
        from SecretChatMessageEntity m
        where m.secretChatId = :secretChatId
          and m.createdAt < :before
          and (m.expiresAt is null or m.expiresAt > :now)
        order by m.createdAt desc
        """)
    List<SecretChatMessageEntity> findRecentVisibleBefore(
            @Param("secretChatId") UUID secretChatId,
            @Param("before") Instant before,
            @Param("now") Instant now,
            Pageable pageable
    );

    @Query("""
        select m
        from SecretChatMessageEntity m
        where m.secretChatId = :secretChatId
          and m.senderUserId <> :readerUserId
          and m.readAt is null
        order by m.createdAt asc
        """)
    List<SecretChatMessageEntity> findUnreadIncoming(
            @Param("secretChatId") UUID secretChatId,
            @Param("readerUserId") UUID readerUserId
    );

    @Query("""
        select m
        from SecretChatMessageEntity m
        where m.expiresAt is not null
          and m.expiresAt <= :now
        order by m.expiresAt asc
        """)
    List<SecretChatMessageEntity> findExpired(
            @Param("now") Instant now,
            Pageable pageable
    );
}
