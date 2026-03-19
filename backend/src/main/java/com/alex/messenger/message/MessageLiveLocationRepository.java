package com.alex.messenger.message;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageLiveLocationRepository extends JpaRepository<MessageLiveLocationEntity, UUID> {

    Optional<MessageLiveLocationEntity> findByMessageId(UUID messageId);

    List<MessageLiveLocationEntity> findAllByMessageIdIn(Collection<UUID> messageIds);
}
