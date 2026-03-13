package com.alex.messenger.chat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatJoinRequestRepository extends JpaRepository<ChatJoinRequestEntity, ChatJoinRequestId> {

    List<ChatJoinRequestEntity> findAllByIdChatIdAndStatusOrderByRequestedAtDesc(UUID chatId, String status);

    Optional<ChatJoinRequestEntity> findByIdChatIdAndIdUserId(UUID chatId, UUID userId);
}
