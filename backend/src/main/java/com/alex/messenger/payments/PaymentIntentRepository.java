package com.alex.messenger.payments;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentIntentRepository extends JpaRepository<PaymentIntentEntity, UUID> {

    List<PaymentIntentEntity> findAllByPayerUserIdOrderByCreatedAtDesc(UUID payerUserId);

    List<PaymentIntentEntity> findAllByRecipientUserIdOrderByCreatedAtDesc(UUID recipientUserId);
}
