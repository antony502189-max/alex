package com.alex.messenger.chat.invite;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatInviteLinkRepository extends JpaRepository<ChatInviteLinkEntity, UUID> {

    List<ChatInviteLinkEntity> findAllByChatIdOrderByCreatedAtDesc(UUID chatId);

    Optional<ChatInviteLinkEntity> findByToken(String token);
}
