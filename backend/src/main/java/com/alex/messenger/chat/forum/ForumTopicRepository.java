package com.alex.messenger.chat.forum;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ForumTopicRepository extends JpaRepository<ForumTopicEntity, UUID> {

    Optional<ForumTopicEntity> findByIdAndChatId(UUID id, UUID chatId);

    Optional<ForumTopicEntity> findByChatIdAndGeneralTopicTrue(UUID chatId);

    long countByChatIdAndHiddenFalse(UUID chatId);

    @Query("""
        select t
        from ForumTopicEntity t
        where t.chatId = :chatId
          and t.hidden = false
        order by t.generalTopic desc, coalesce(t.lastMessageAt, t.createdAt) desc, t.createdAt asc
        """)
    List<ForumTopicEntity> findVisibleTopics(@Param("chatId") UUID chatId);
}
