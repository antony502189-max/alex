package com.alex.messenger.monetization;

import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
public class MonetizationAlertTriageDispatcher {

    private final MonetizationService monetizationService;
    private final int batchSize;

    public MonetizationAlertTriageDispatcher(
            MonetizationService monetizationService,
            @Value("${alex.monetization.alert-triage.batch-size:20}") int batchSize
    ) {
        this.monetizationService = monetizationService;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${alex.monetization.alert-triage.dispatch-interval-ms:60000}")
    @Transactional
    void dispatchAlertTriage() {
        int processed = monetizationService.processAlertTriage(Instant.now(), batchSize);
        if (processed > 0) {
            log.info("Processed {} monetization alert triage events", processed);
        }
    }
}
