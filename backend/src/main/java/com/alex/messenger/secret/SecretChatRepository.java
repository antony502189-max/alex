package com.alex.messenger.secret;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SecretChatRepository extends JpaRepository<SecretChatEntity, UUID> {

    @Query("""
        select sc
        from SecretChatEntity sc
        where
            sc.status in ('PENDING', 'ACTIVE')
            and
            (
                (sc.initiatorUserId = :userId and sc.initiatorSessionId = :sessionId)
                or
                (
                    sc.recipientUserId = :userId
                    and (
                        sc.recipientSessionId = :sessionId
                        or (sc.recipientSessionId is null and sc.status = 'PENDING')
                    )
                )
            )
        order by coalesce(sc.lastMessageAt, sc.acceptedAt, sc.createdAt) desc
        """)
    List<SecretChatEntity> findVisibleChats(
            @Param("userId") UUID userId,
            @Param("sessionId") UUID sessionId
    );

    @Query("""
        select sc
        from SecretChatEntity sc
        where sc.id = :secretChatId
          and (
              (sc.initiatorUserId = :userId and sc.initiatorSessionId = :sessionId)
              or
              (
                  sc.recipientUserId = :userId
                  and (
                      sc.recipientSessionId = :sessionId
                      or (sc.recipientSessionId is null and sc.status = 'PENDING')
                  )
              )
          )
        """)
    Optional<SecretChatEntity> findAccessible(
            @Param("secretChatId") UUID secretChatId,
            @Param("userId") UUID userId,
            @Param("sessionId") UUID sessionId
    );
}
