package com.alex.messenger.monetization;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SponsoredMessageEventRepository extends JpaRepository<SponsoredMessageEventEntity, UUID> {

    boolean existsBySponsoredMessageIdAndViewerUserIdAndEventType(UUID sponsoredMessageId, UUID viewerUserId, String eventType);

    long countBySponsoredMessageIdAndEventType(UUID sponsoredMessageId, String eventType);

    List<SponsoredMessageEventEntity> findAllBySponsoredMessageIdIn(Collection<UUID> sponsoredMessageIds);
}
