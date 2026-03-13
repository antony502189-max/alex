package com.alex.messenger.premium;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelBoostRepository extends JpaRepository<ChannelBoostEntity, UUID> {

    List<ChannelBoostEntity> findAllByChannelChatIdOrderByUpdatedAtDesc(UUID channelChatId);

    Optional<ChannelBoostEntity> findByChannelChatIdAndBoostedByUserId(UUID channelChatId, UUID boostedByUserId);
}
