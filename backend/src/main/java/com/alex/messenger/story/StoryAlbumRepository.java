package com.alex.messenger.story;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoryAlbumRepository extends JpaRepository<StoryAlbumEntity, UUID> {

    List<StoryAlbumEntity> findAllByOwnerUserIdOrderByPositionAscCreatedAtAsc(UUID ownerUserId);

    List<StoryAlbumEntity> findAllByOwnerChatIdOrderByPositionAscCreatedAtAsc(UUID ownerChatId);
}
