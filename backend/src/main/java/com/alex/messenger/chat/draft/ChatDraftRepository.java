package com.alex.messenger.chat.draft;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatDraftRepository extends JpaRepository<ChatDraftEntity, ChatDraftId> {

    List<ChatDraftEntity> findAllByIdUserIdAndIdChatIdIn(UUID userId, Collection<UUID> chatIds);
}
