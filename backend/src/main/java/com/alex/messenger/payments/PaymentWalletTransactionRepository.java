package com.alex.messenger.payments;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentWalletTransactionRepository extends JpaRepository<PaymentWalletTransactionEntity, UUID> {

    List<PaymentWalletTransactionEntity> findAllByWalletUserIdOrderByCreatedAtDesc(UUID walletUserId);

    @Query("""
        select coalesce(sum(t.amountUnits), 0)
        from PaymentWalletTransactionEntity t
        where t.walletUserId = :walletUserId
          and t.direction = :direction
          and t.transactionType in :transactionTypes
        """)
    Long sumAmountUnitsByWalletUserIdAndDirectionAndTransactionTypeIn(
            @Param("walletUserId") UUID walletUserId,
            @Param("direction") String direction,
            @Param("transactionTypes") Collection<String> transactionTypes
    );

    @Query("""
        select count(t)
        from PaymentWalletTransactionEntity t
        where t.walletUserId = :walletUserId
          and t.transactionType in :transactionTypes
        """)
    long countByWalletUserIdAndTransactionTypeIn(
            @Param("walletUserId") UUID walletUserId,
            @Param("transactionTypes") Collection<String> transactionTypes
    );
}
