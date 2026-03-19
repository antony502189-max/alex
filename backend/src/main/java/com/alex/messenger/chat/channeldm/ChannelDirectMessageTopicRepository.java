package com.alex.messenger.chat.channeldm;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChannelDirectMessageTopicRepository extends JpaRepository<ChannelDirectMessageTopicEntity, UUID> {

    Optional<ChannelDirectMessageTopicEntity> findByDirectChatId(UUID directChatId);

    @Query("""
        select t
        from ChannelDirectMessageTopicEntity t
        where t.channelChatId = :channelChatId
          and (:participantUserId is null or t.participantUserId = :participantUserId)
        order by coalesce(t.lastMessageAt, t.createdAt) desc, t.createdAt desc
        """)
    List<ChannelDirectMessageTopicEntity> findVisible(
            @Param("channelChatId") UUID channelChatId,
            @Param("participantUserId") UUID participantUserId,
            Pageable pageable
    );
}
