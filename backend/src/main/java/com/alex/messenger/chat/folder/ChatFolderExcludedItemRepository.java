package com.alex.messenger.chat.folder;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatFolderExcludedItemRepository extends JpaRepository<ChatFolderExcludedItemEntity, ChatFolderExcludedItemId> {

    List<ChatFolderExcludedItemEntity> findAllByIdFolderId(UUID folderId);

    List<ChatFolderExcludedItemEntity> findAllByIdFolderIdIn(Collection<UUID> folderIds);

    void deleteAllByIdFolderId(UUID folderId);
}
