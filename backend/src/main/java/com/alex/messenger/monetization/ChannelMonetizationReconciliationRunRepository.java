package com.alex.messenger.monetization;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelMonetizationReconciliationRunRepository extends JpaRepository<ChannelMonetizationReconciliationRunEntity, UUID> {

    List<ChannelMonetizationReconciliationRunEntity> findAllByChannelChatIdOrderByCreatedAtDesc(UUID channelChatId);
}
