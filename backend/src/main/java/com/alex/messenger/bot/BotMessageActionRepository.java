package com.alex.messenger.bot;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BotMessageActionRepository extends JpaRepository<BotMessageActionEntity, UUID> {

    List<BotMessageActionEntity> findAllByMessageIdOrderBySortOrderAsc(UUID messageId);

    void deleteAllByMessageId(UUID messageId);
}
