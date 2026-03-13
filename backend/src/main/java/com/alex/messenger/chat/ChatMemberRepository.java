package com.alex.messenger.chat;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMemberRepository extends JpaRepository<ChatMemberEntity, ChatMemberId> {

    List<ChatMemberEntity> findAllByIdUserId(UUID userId);

    List<ChatMemberEntity> findAllByIdChatId(UUID chatId);

    long countByIdChatId(UUID chatId);

    boolean existsByIdChatIdAndIdUserId(UUID chatId, UUID userId);

    @Query("""
        select cm
        from ChatMemberEntity cm
        join ChatEntity c on c.id = cm.id.chatId
        where cm.id.userId = :userId and cm.archived = :archived
        order by coalesce(c.lastMessageAt, c.createdAt) desc
        """)
    List<ChatMemberEntity> findMembershipsOrderedForUser(
            @Param("userId") UUID userId,
            @Param("archived") boolean archived
    );
}
