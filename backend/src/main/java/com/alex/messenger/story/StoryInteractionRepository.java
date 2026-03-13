package com.alex.messenger.story;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoryInteractionRepository extends JpaRepository<StoryInteractionEntity, UUID> {

    List<StoryInteractionEntity> findAllByStoryIdOrderByCreatedAtDesc(UUID storyId);

    Optional<StoryInteractionEntity> findFirstByStoryIdAndActorUserIdAndInteractionType(
            UUID storyId,
            UUID actorUserId,
            String interactionType
    );

    long deleteByStoryIdAndActorUserIdAndInteractionType(UUID storyId, UUID actorUserId, String interactionType);
}
