package com.alex.messenger.bot;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BotAccountRepository extends JpaRepository<BotAccountEntity, UUID> {

    List<BotAccountEntity> findAllByOwnerUserIdOrderByCreatedAtDesc(UUID ownerUserId);

    Optional<BotAccountEntity> findByBotUserIdAndOwnerUserId(UUID botUserId, UUID ownerUserId);

    Optional<BotAccountEntity> findByApiTokenHash(String apiTokenHash);
}
