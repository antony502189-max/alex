package com.alex.messenger.monetization;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelMonetizationArtifactAlertCommentRepository
        extends JpaRepository<ChannelMonetizationArtifactAlertCommentEntity, UUID> {

    List<ChannelMonetizationArtifactAlertCommentEntity> findAllByAlertIdOrderByCreatedAtAsc(UUID alertId);
}
