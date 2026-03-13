package com.alex.messenger.bot;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BotCallbackQueryRepository extends JpaRepository<BotCallbackQueryEntity, UUID> {

    Optional<BotCallbackQueryEntity> findByIdAndBotUserId(UUID id, UUID botUserId);
}
