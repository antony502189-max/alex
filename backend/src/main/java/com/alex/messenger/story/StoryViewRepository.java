package com.alex.messenger.story;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoryViewRepository extends JpaRepository<StoryViewEntity, StoryViewId> {

    List<StoryViewEntity> findAllByIdStoryId(UUID storyId);

    List<StoryViewEntity> findAllByIdViewerUserIdAndIdStoryIdIn(UUID viewerUserId, Collection<UUID> storyIds);
}
