package com.alex.messenger.business;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessOperatorAssignmentRepository
        extends JpaRepository<BusinessOperatorAssignmentEntity, BusinessOperatorAssignmentId> {

    Optional<BusinessOperatorAssignmentEntity> findByIdOwnerUserIdAndIdChatId(UUID ownerUserId, UUID chatId);
}
