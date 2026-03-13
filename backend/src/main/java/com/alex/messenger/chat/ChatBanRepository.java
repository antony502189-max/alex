package com.alex.messenger.chat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatBanRepository extends JpaRepository<ChatBanEntity, ChatBanId> {

    List<ChatBanEntity> findAllByIdChatIdOrderByBannedAtDesc(UUID chatId);

    List<ChatBanEntity> findAllByIdChatIdAndBannedUntilBefore(UUID chatId, Instant bannedUntil);
}
