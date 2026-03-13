package com.alex.messenger.bot;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BotPreCheckoutQueryRepository extends JpaRepository<BotPreCheckoutQueryEntity, UUID> {

    Optional<BotPreCheckoutQueryEntity> findByIdAndBotUserId(UUID id, UUID botUserId);

    Optional<BotPreCheckoutQueryEntity> findByIdAndFromUserId(UUID id, UUID fromUserId);
}
