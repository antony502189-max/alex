package com.alex.messenger.monetization;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SponsoredMessageRepository extends JpaRepository<SponsoredMessageEntity, UUID> {

    List<SponsoredMessageEntity> findAllByChannelChatIdOrderByCreatedAtDesc(UUID channelChatId);
}
