package com.alex.messenger.chat.folder;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatFolderItemRepository extends JpaRepository<ChatFolderItemEntity, ChatFolderItemId> {

    List<ChatFolderItemEntity> findAllByIdFolderId(UUID folderId);

    List<ChatFolderItemEntity> findAllByIdFolderIdIn(Collection<UUID> folderIds);

    void deleteAllByIdFolderId(UUID folderId);
}
