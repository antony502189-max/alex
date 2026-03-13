package com.alex.messenger.bot;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BotCommandRepository extends JpaRepository<BotCommandEntity, UUID> {

    List<BotCommandEntity> findAllByBotUserIdOrderByPositionAscCreatedAtAsc(UUID botUserId);

    void deleteAllByBotUserId(UUID botUserId);
}
