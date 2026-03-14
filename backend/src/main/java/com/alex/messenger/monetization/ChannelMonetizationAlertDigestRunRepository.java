package com.alex.messenger.monetization;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelMonetizationAlertDigestRunRepository extends JpaRepository<ChannelMonetizationAlertDigestRunEntity, UUID> {

    List<ChannelMonetizationAlertDigestRunEntity> findAllByChannelChatIdOrderByCreatedAtDesc(UUID channelChatId);

    java.util.Optional<ChannelMonetizationAlertDigestRunEntity> findFirstByChannelChatIdOrderByCreatedAtDesc(UUID channelChatId);
}
