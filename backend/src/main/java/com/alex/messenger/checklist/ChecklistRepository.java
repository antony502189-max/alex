package com.alex.messenger.checklist;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChecklistRepository extends JpaRepository<ChecklistEntity, UUID> {

    List<ChecklistEntity> findAllByChatIdOrderByUpdatedAtDescCreatedAtDesc(UUID chatId);

    List<ChecklistEntity> findAllByChatIdAndTopicIdOrderByUpdatedAtDescCreatedAtDesc(UUID chatId, UUID topicId);
}
