package com.alex.messenger.call;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CallJoinLinkRepository extends JpaRepository<CallJoinLinkEntity, UUID> {

    List<CallJoinLinkEntity> findAllByChatIdOrderByCreatedAtDesc(UUID chatId);

    Optional<CallJoinLinkEntity> findByToken(String token);

    boolean existsByToken(String token);
}
