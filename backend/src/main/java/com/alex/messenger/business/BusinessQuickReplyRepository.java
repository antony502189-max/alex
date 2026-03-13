package com.alex.messenger.business;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessQuickReplyRepository extends JpaRepository<BusinessQuickReplyEntity, UUID> {

    List<BusinessQuickReplyEntity> findAllByUserIdOrderByPositionAscCreatedAtAsc(UUID userId);

    Optional<BusinessQuickReplyEntity> findByIdAndUserId(UUID id, UUID userId);
}
