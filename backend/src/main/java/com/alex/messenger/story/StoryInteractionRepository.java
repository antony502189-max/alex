package com.alex.messenger.story;

import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoryInteractionRepository extends JpaRepository<StoryInteractionEntity, UUID> {

    List<StoryInteractionEntity> findAllByStoryIdOrderByCreatedAtDesc(UUID storyId);

    Optional<StoryInteractionEntity> findFirstByStoryIdAndActorUserIdAndInteractionType(
            UUID storyId,
            UUID actorUserId,
            String interactionType
    );

    long countByStoryIdAndSeenAtIsNull(UUID storyId);

    long countByStoryIdInAndSeenAtIsNull(Collection<UUID> storyIds);

    @Transactional
    @Modifying
    @Query("""
            update StoryInteractionEntity interaction
            set interaction.seenAt = :seenAt
            where interaction.storyId = :storyId
              and interaction.seenAt is null
            """)
    int markSeenByStoryId(@Param("storyId") UUID storyId, @Param("seenAt") Instant seenAt);

    long deleteByStoryIdAndActorUserIdAndInteractionType(UUID storyId, UUID actorUserId, String interactionType);
}
