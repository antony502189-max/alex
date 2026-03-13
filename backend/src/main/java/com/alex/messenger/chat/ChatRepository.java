package com.alex.messenger.chat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRepository extends JpaRepository<ChatEntity, UUID> {

    Optional<ChatEntity> findByParticipantLowIdAndParticipantHighId(UUID participantLowId, UUID participantHighId);

    Optional<ChatEntity> findByChatTypeAndCreatedBy(String chatType, UUID createdBy);

    Optional<ChatEntity> findByPublicUsernameIgnoreCase(String publicUsername);

    List<ChatEntity> findAllByAutoDeleteSecondsIsNotNull();

    @Query("""
        select c
        from ChatEntity c
        where c.participantLowId = :userId or c.participantHighId = :userId
        order by coalesce(c.lastMessageAt, c.createdAt) desc
        """)
    List<ChatEntity> findAllForUser(@Param("userId") UUID userId);

    @Query("""
        select c
        from ChatEntity c
        where c.chatType in ('GROUP', 'CHANNEL')
          and c.publicUsername is not null
          and (
              lower(c.publicUsername) like lower(concat('%', :query, '%'))
              or lower(coalesce(c.title, '')) like lower(concat('%', :query, '%'))
              or lower(coalesce(c.about, '')) like lower(concat('%', :query, '%'))
          )
        order by
            case when lower(c.publicUsername) = lower(:query) then 0 else 1 end,
            coalesce(c.lastMessageAt, c.createdAt) desc
        """)
    List<ChatEntity> searchPublicChats(@Param("query") String query);
}
