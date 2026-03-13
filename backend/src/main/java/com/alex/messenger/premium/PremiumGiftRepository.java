package com.alex.messenger.premium;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PremiumGiftRepository extends JpaRepository<PremiumGiftEntity, UUID> {

    List<PremiumGiftEntity> findAllByRecipientUserIdOrderByCreatedAtDesc(UUID recipientUserId);

    List<PremiumGiftEntity> findAllBySenderUserIdOrderByCreatedAtDesc(UUID senderUserId);
}
