package com.alex.messenger.monetization;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelMonetizationOwnerReminderDigestRunRepository
        extends JpaRepository<ChannelMonetizationOwnerReminderDigestRunEntity, UUID> {

    List<ChannelMonetizationOwnerReminderDigestRunEntity> findAllBySubscriptionIdOrderByProcessedAtDesc(UUID subscriptionId);
}
