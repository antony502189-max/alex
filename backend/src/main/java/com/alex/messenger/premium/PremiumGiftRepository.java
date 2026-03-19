package com.alex.messenger.premium;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PremiumGiftRepository extends JpaRepository<PremiumGiftEntity, UUID> {

    List<PremiumGiftEntity> findAllByRecipientUserIdOrderByCreatedAtDesc(UUID recipientUserId);

    List<PremiumGiftEntity> findAllBySenderUserIdOrderByCreatedAtDesc(UUID senderUserId);

    long countByRecipientUserId(UUID recipientUserId);

    long countBySenderUserId(UUID senderUserId);

    @Query("""
        select coalesce(sum(g.premiumDaysGranted), 0)
        from PremiumGiftEntity g
        where g.recipientUserId = :userId
        """)
    Long sumPremiumDaysByRecipientUserId(@Param("userId") UUID userId);

    @Query("""
        select coalesce(sum(g.premiumDaysGranted), 0)
        from PremiumGiftEntity g
        where g.senderUserId = :userId
        """)
    Long sumPremiumDaysBySenderUserId(@Param("userId") UUID userId);
}
