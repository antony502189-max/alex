package com.alex.messenger.bot;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class BotWebhookDispatcher {

    private final BotUpdateService botUpdateService;

    @Scheduled(fixedDelayString = "${alex.bots.webhook.dispatch-interval-ms:5000}")
    @Transactional
    void dispatchWebhookUpdates() {
        List<BotUpdateEntity> updates = botUpdateService.lockWebhookDeliveryBatch(20);
        for (BotUpdateEntity update : updates) {
            botUpdateService.deliverWebhookUpdate(update);
        }
    }
}
