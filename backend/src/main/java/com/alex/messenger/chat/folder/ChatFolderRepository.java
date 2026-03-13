package com.alex.messenger.chat.folder;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatFolderRepository extends JpaRepository<ChatFolderEntity, UUID> {

    List<ChatFolderEntity> findAllByOwnerUserIdOrderByPositionAscTitleAsc(UUID ownerUserId);
}
