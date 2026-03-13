package com.alex.messenger.bot;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BotInlineResultCacheRepository extends JpaRepository<BotInlineResultCacheEntity, UUID> {

    List<BotInlineResultCacheEntity> findAllByBotUserIdAndQueryTextAndCachedUntilAfterOrderByCreatedAtAsc(
            UUID botUserId,
            String queryText,
            Instant now
    );

    void deleteAllByBotUserIdAndQueryText(UUID botUserId, String queryText);
}
