package com.alex.messenger.monetization;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelMonetizationExportArtifactRepository extends JpaRepository<ChannelMonetizationExportArtifactEntity, UUID> {

    List<ChannelMonetizationExportArtifactEntity> findAllByChannelChatIdOrderByCreatedAtDesc(UUID channelChatId);

    List<ChannelMonetizationExportArtifactEntity> findAllByChannelChatIdAndArtifactTypeOrderByCreatedAtDesc(
            UUID channelChatId,
            String artifactType
    );

    Optional<ChannelMonetizationExportArtifactEntity> findFirstByChannelChatIdAndArtifactTypeOrderByCreatedAtDesc(
            UUID channelChatId,
            String artifactType
    );

    Optional<ChannelMonetizationExportArtifactEntity> findByIdAndChannelChatId(UUID id, UUID channelChatId);
}
