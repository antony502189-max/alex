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
public class MonetizationAlertDigestDispatcher {

    private final MonetizationService monetizationService;
    private final Duration suppressionDelay;
    private final int batchSize;

    public MonetizationAlertDigestDispatcher(
            MonetizationService monetizationService,
            @Value("${alex.monetization.alert-digests.suppression-delay:PT30M}") Duration suppressionDelay,
            @Value("${alex.monetization.alert-digests.batch-size:20}") int batchSize
    ) {
        this.monetizationService = monetizationService;
        this.suppressionDelay = suppressionDelay;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${alex.monetization.alert-digests.dispatch-interval-ms:60000}")
    @Transactional
    void dispatchAlertDigests() {
        int processed = monetizationService.processAlertDigests(Instant.now().minus(suppressionDelay), batchSize);
        if (processed > 0) {
            log.info("Processed {} monetization alert digests", processed);
        }
    }
}
