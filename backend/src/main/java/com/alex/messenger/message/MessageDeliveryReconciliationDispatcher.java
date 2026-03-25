package com.alex.messenger.message;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MessageDeliveryReconciliationDispatcher {

    private final MessageDeliveryService messageDeliveryService;

    @Scheduled(fixedDelayString = "${alex.message.delivery.reconciliation.dispatch-interval-ms:60000}")
    void reconcileRecentDirectChats() {
        messageDeliveryService.reconcileRecentDirectChats();
    }
}
