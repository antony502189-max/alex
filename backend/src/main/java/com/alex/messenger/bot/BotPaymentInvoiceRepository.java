package com.alex.messenger.bot;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BotPaymentInvoiceRepository extends JpaRepository<BotPaymentInvoiceEntity, UUID> {

    Optional<BotPaymentInvoiceEntity> findByMessageId(UUID messageId);
}
