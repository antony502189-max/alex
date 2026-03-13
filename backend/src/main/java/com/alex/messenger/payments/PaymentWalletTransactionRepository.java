package com.alex.messenger.payments;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentWalletTransactionRepository extends JpaRepository<PaymentWalletTransactionEntity, UUID> {

    List<PaymentWalletTransactionEntity> findAllByWalletUserIdOrderByCreatedAtDesc(UUID walletUserId);
}
