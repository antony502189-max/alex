package com.alex.messenger.monetization;

import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
public class MonetizationAlertReminderDispatcher {

    private final MonetizationService monetizationService;
    private final int batchSize;

    public MonetizationAlertReminderDispatcher(
            MonetizationService monetizationService,
            @Value("${alex.monetization.alert-reminders.batch-size:20}") int batchSize
    ) {
        this.monetizationService = monetizationService;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${alex.monetization.alert-reminders.dispatch-interval-ms:60000}")
    @Transactional
    void dispatchAlertReminders() {
        int processed = monetizationService.processAlertReminders(Instant.now(), batchSize);
        if (processed > 0) {
            log.info("Processed {} monetization alert reminders", processed);
        }
    }
}
