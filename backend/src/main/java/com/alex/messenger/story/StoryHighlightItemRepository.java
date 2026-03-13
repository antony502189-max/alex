package com.alex.messenger.story;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoryHighlightItemRepository extends JpaRepository<StoryHighlightItemEntity, UUID> {

    List<StoryHighlightItemEntity> findAllByHighlightIdOrderByPositionAscCreatedAtAsc(UUID highlightId);

    Optional<StoryHighlightItemEntity> findFirstByHighlightIdAndStoryId(UUID highlightId, UUID storyId);
}
