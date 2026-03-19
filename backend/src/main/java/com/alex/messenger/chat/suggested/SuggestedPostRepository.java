package com.alex.messenger.chat.suggested;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SuggestedPostRepository extends JpaRepository<SuggestedPostEntity, UUID> {

    Optional<SuggestedPostEntity> findByIdAndChatId(UUID id, UUID chatId);

    @Query("""
        select sp
        from SuggestedPostEntity sp
        where sp.chatId = :chatId
          and (:submittedByUserId is null or sp.submittedByUserId = :submittedByUserId)
          and (:status is null or sp.status = :status)
        order by sp.createdAt desc
        """)
    List<SuggestedPostEntity> findVisible(
            @Param("chatId") UUID chatId,
            @Param("submittedByUserId") UUID submittedByUserId,
            @Param("status") String status,
            Pageable pageable
    );
}
