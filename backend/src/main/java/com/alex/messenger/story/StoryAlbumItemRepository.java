package com.alex.messenger.story;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoryAlbumItemRepository extends JpaRepository<StoryAlbumItemEntity, UUID> {

    List<StoryAlbumItemEntity> findAllByAlbumIdOrderByPositionAscCreatedAtAsc(UUID albumId);
}
