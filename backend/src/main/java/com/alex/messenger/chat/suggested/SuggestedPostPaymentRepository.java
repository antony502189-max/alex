package com.alex.messenger.chat.suggested;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SuggestedPostPaymentRepository extends JpaRepository<SuggestedPostPaymentEntity, UUID> {

    Optional<SuggestedPostPaymentEntity> findBySuggestedPostId(UUID suggestedPostId);

    List<SuggestedPostPaymentEntity> findAllBySuggestedPostIdIn(Collection<UUID> suggestedPostIds);
}
