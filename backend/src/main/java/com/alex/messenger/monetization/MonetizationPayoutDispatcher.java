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
public class MonetizationPayoutDispatcher {

    private final MonetizationService monetizationService;
    private final Duration settlementDelay;
    private final int batchSize;

    public MonetizationPayoutDispatcher(
            MonetizationService monetizationService,
            @Value("${alex.monetization.payout.settlement-delay:PT15M}") Duration settlementDelay,
            @Value("${alex.monetization.payout.batch-size:20}") int batchSize
    ) {
        this.monetizationService = monetizationService;
        this.settlementDelay = settlementDelay;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${alex.monetization.payout.dispatch-interval-ms:60000}")
    @Transactional
    void dispatchReadyPayouts() {
        int processed = monetizationService.processReadyPayouts(Instant.now().minus(settlementDelay), batchSize);
        if (processed > 0) {
            log.info("Processed {} monetization payout batch entries", processed);
        }
    }
}
