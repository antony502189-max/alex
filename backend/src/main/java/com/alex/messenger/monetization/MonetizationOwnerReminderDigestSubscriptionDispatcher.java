package com.alex.messenger.monetization;

import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
public class MonetizationOwnerReminderDigestSubscriptionDispatcher {

    private final MonetizationService monetizationService;
    private final int batchSize;

    public MonetizationOwnerReminderDigestSubscriptionDispatcher(
            MonetizationService monetizationService,
            @Value("${alex.monetization.personal-reminder-digests.batch-size:20}") int batchSize
    ) {
        this.monetizationService = monetizationService;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${alex.monetization.personal-reminder-digests.dispatch-interval-ms:60000}")
    @Transactional
    void dispatchOwnerReminderDigestSubscriptions() {
        int processed = monetizationService.processOwnerReminderDigestSubscriptions(Instant.now(), batchSize);
        if (processed > 0) {
            log.info("Processed {} monetization owner reminder digest subscriptions", processed);
        }
    }
}
