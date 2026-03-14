package com.alex.messenger.monetization;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelMonetizationProviderSyncRunRepository extends JpaRepository<ChannelMonetizationProviderSyncRunEntity, UUID> {

    List<ChannelMonetizationProviderSyncRunEntity> findAllByChannelChatIdOrderByCreatedAtDesc(UUID channelChatId);
}
