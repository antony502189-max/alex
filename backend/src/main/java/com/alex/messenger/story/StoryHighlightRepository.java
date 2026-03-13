package com.alex.messenger.story;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoryHighlightRepository extends JpaRepository<StoryHighlightEntity, UUID> {

    List<StoryHighlightEntity> findAllByOwnerUserIdOrderByPositionAscCreatedAtAsc(UUID ownerUserId);
}
