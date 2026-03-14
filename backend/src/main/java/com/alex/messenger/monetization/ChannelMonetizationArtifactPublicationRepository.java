package com.alex.messenger.monetization;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelMonetizationArtifactPublicationRepository
        extends JpaRepository<ChannelMonetizationArtifactPublicationEntity, UUID> {

    List<ChannelMonetizationArtifactPublicationEntity> findAllByArtifactIdOrderByPublishedAtDesc(UUID artifactId);
}
