package com.alex.messenger.monetization;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelMonetizationArtifactSubscriptionFailureRepository
        extends JpaRepository<ChannelMonetizationArtifactSubscriptionFailureEntity, UUID> {

    List<ChannelMonetizationArtifactSubscriptionFailureEntity> findAllBySubscriptionIdOrderByFailedAtDesc(UUID subscriptionId);

    List<ChannelMonetizationArtifactSubscriptionFailureEntity> findAllByChannelChatIdOrderByFailedAtDesc(UUID channelChatId);
}
