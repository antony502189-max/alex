package com.alex.messenger.bot;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BotPaymentReceiptRepository extends JpaRepository<BotPaymentReceiptEntity, UUID> {

    Optional<BotPaymentReceiptEntity> findByPreCheckoutQueryId(UUID preCheckoutQueryId);

    Optional<BotPaymentReceiptEntity> findByPaymentIntentId(UUID paymentIntentId);

    Optional<BotPaymentReceiptEntity> findByInvoiceMessageIdOrServiceMessageIdOrRefundMessageId(
            UUID invoiceMessageId,
            UUID serviceMessageId,
            UUID refundMessageId
    );
}
