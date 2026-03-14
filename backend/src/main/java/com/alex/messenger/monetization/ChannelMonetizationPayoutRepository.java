package com.alex.messenger.monetization;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelMonetizationPayoutRepository extends JpaRepository<ChannelMonetizationPayoutEntity, UUID> {

    List<ChannelMonetizationPayoutEntity> findAllByChannelChatIdOrderByCreatedAtDesc(UUID channelChatId);

    long countByChannelChatId(UUID channelChatId);
}
