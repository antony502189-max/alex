package com.alex.messenger.chat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatPinEventRepository extends JpaRepository<ChatPinEventEntity, UUID> {

    Optional<ChatPinEventEntity> findFirstByChatIdAndActiveTrueOrderByPinnedAtDesc(UUID chatId);

    List<ChatPinEventEntity> findAllByChatIdOrderByPinnedAtDesc(UUID chatId, Pageable pageable);
}
