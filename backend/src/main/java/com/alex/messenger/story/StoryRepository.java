package com.alex.messenger.story;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoryRepository extends JpaRepository<StoryEntity, UUID> {

    List<StoryEntity> findAllByExpiresAtAfterOrderByCreatedAtDesc(Instant now);

    List<StoryEntity> findAllByOwnerUserIdInAndExpiresAtAfterOrderByCreatedAtDesc(Collection<UUID> ownerUserIds, Instant now);

    List<StoryEntity> findAllByOwnerUserIdAndExpiresAtAfterOrderByCreatedAtDesc(UUID ownerUserId, Instant now);

    List<StoryEntity> findAllByOwnerUserIdOrderByCreatedAtDesc(UUID ownerUserId);

    Optional<StoryEntity> findByIdAndExpiresAtAfter(UUID storyId, Instant now);
}
