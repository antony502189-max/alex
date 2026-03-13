package com.alex.messenger.payments;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentWalletAccountRepository extends JpaRepository<PaymentWalletAccountEntity, UUID> {
}
