package com.alex.messenger.story;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoryLiveSessionRepository extends JpaRepository<StoryLiveSessionEntity, UUID> {

    Optional<StoryLiveSessionEntity> findFirstByStoryIdAndStatusOrderByStartedAtDesc(UUID storyId, String status);

    List<StoryLiveSessionEntity> findAllByStoryIdInAndStatus(Collection<UUID> storyIds, String status);
}
