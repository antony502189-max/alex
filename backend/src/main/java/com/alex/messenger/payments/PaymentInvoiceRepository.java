package com.alex.messenger.payments;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentInvoiceRepository extends JpaRepository<PaymentInvoiceEntity, UUID> {

    List<PaymentInvoiceEntity> findAllByCreatedByUserIdOrderByCreatedAtDesc(UUID createdByUserId);

    List<PaymentInvoiceEntity> findAllByRecipientUserIdOrderByCreatedAtDesc(UUID recipientUserId);
}
