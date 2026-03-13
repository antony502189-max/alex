package com.alex.messenger.chat;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatAdminLogRepository extends JpaRepository<ChatAdminLogEntity, UUID> {

    List<ChatAdminLogEntity> findAllByChatIdOrderByCreatedAtDesc(UUID chatId, Pageable pageable);
}
