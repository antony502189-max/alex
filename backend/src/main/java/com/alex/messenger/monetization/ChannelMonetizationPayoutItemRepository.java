package com.alex.messenger.monetization;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelMonetizationPayoutItemRepository extends JpaRepository<ChannelMonetizationPayoutItemEntity, UUID> {

    List<ChannelMonetizationPayoutItemEntity> findAllByPayoutIdInOrderByCreatedAtAsc(Collection<UUID> payoutIds);
}
