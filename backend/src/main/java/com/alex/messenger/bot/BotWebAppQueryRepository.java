package com.alex.messenger.bot;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BotWebAppQueryRepository extends JpaRepository<BotWebAppQueryEntity, UUID> {

    Optional<BotWebAppQueryEntity> findByIdAndBotUserId(UUID id, UUID botUserId);
}
