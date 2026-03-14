package com.alex.messenger.monetization;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelMonetizationArtifactAlertAuditEventRepository
        extends JpaRepository<ChannelMonetizationArtifactAlertAuditEventEntity, UUID> {

    List<ChannelMonetizationArtifactAlertAuditEventEntity> findAllByAlertIdOrderByCreatedAtAsc(UUID alertId);
}
