package com.alex.messenger.business;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessChatTagRepository extends JpaRepository<BusinessChatTagEntity, UUID> {

    List<BusinessChatTagEntity> findAllByOwnerUserIdAndChatIdOrderByPositionAscCreatedAtAsc(UUID ownerUserId, UUID chatId);

    void deleteAllByOwnerUserIdAndChatId(UUID ownerUserId, UUID chatId);
}
