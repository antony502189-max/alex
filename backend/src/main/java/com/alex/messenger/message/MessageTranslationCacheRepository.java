package com.alex.messenger.message;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageTranslationCacheRepository extends JpaRepository<MessageTranslationCacheEntity, UUID> {

    Optional<MessageTranslationCacheEntity> findByMessageIdAndTargetLanguage(UUID messageId, String targetLanguage);

    List<MessageTranslationCacheEntity> findAllByMessageId(UUID messageId);

    List<MessageTranslationCacheEntity> findAllByMessageIdIn(Collection<UUID> messageIds);

    void deleteByMessageId(UUID messageId);
}
