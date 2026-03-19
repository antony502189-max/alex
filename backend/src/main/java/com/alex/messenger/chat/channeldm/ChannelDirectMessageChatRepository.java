package com.alex.messenger.chat.channeldm;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChannelDirectMessageChatRepository extends JpaRepository<ChannelDirectMessageChatEntity, UUID> {

    Optional<ChannelDirectMessageChatEntity> findByChannelChatIdAndParticipantUserId(UUID channelChatId, UUID participantUserId);

    Optional<ChannelDirectMessageChatEntity> findByDirectChatId(UUID directChatId);

    @Query("""
        select c
        from ChannelDirectMessageChatEntity c
        where c.channelChatId = :channelChatId
          and (:participantUserId is null or c.participantUserId = :participantUserId)
        order by c.updatedAt desc, c.createdAt desc
        """)
    List<ChannelDirectMessageChatEntity> findVisible(
            @Param("channelChatId") UUID channelChatId,
            @Param("participantUserId") UUID participantUserId,
            Pageable pageable
    );

    long countByChannelChatId(UUID channelChatId);
}
