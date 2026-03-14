package com.alex.messenger.monetization;

import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
public class MonetizationWithdrawalReconciliationDispatcher {

    private final MonetizationService monetizationService;
    private final Duration processingDelay;
    private final int batchSize;

    public MonetizationWithdrawalReconciliationDispatcher(
            MonetizationService monetizationService,
            @Value("${alex.monetization.reconciliation.processing-delay:PT1M}") Duration processingDelay,
            @Value("${alex.monetization.reconciliation.batch-size:20}") int batchSize
    ) {
        this.monetizationService = monetizationService;
        this.processingDelay = processingDelay;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${alex.monetization.reconciliation.dispatch-interval-ms:60000}")
    @Transactional
    void dispatchProcessingWithdrawals() {
        int processed = monetizationService.processWithdrawalReconciliation(Instant.now().minus(processingDelay), batchSize);
        if (processed > 0) {
            log.info("Reconciled {} monetization withdrawals", processed);
        }
    }
}
