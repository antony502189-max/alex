package com.alex.messenger.message;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageReactionRepository extends JpaRepository<MessageReactionEntity, MessageReactionId> {

    List<MessageReactionEntity> findAllByIdMessageId(UUID messageId);

    List<MessageReactionEntity> findAllByIdMessageIdIn(Collection<UUID> messageIds);

    void deleteAllByIdMessageId(UUID messageId);
}
