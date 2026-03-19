package com.alex.messenger.message.repeating;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepeatingMessageRuleRepository extends JpaRepository<RepeatingMessageRuleEntity, UUID> {

    Optional<RepeatingMessageRuleEntity> findByIdAndSenderId(UUID id, UUID senderId);

    Optional<RepeatingMessageRuleEntity> findBySenderIdAndClientRuleId(UUID senderId, UUID clientRuleId);
}
