package com.alex.messenger.message;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AutoDeleteMessageDispatcher {

    private final MessageService messageService;

    @Scheduled(fixedDelayString = "${alex.auto-delete.dispatch-interval-ms:60000}")
    void autoDeleteExpiredMessages() {
        messageService.autoDeleteExpiredMessages(Instant.now(), 100);
    }
}
