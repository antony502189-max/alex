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
public class MonetizationArtifactSubscriptionDispatcher {

    private final MonetizationService monetizationService;
    private final Duration deliveryDelay;
    private final int batchSize;

    public MonetizationArtifactSubscriptionDispatcher(
            MonetizationService monetizationService,
            @Value("${alex.monetization.artifact-subscriptions.delivery-delay:PT15M}") Duration deliveryDelay,
            @Value("${alex.monetization.artifact-subscriptions.batch-size:20}") int batchSize
    ) {
        this.monetizationService = monetizationService;
        this.deliveryDelay = deliveryDelay;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${alex.monetization.artifact-subscriptions.dispatch-interval-ms:60000}")
    @Transactional
    void dispatchArtifactSubscriptions() {
        int processed = monetizationService.processArtifactSubscriptions(Instant.now().minus(deliveryDelay), batchSize);
        if (processed > 0) {
            log.info("Processed {} monetization artifact subscriptions", processed);
        }
    }
}
