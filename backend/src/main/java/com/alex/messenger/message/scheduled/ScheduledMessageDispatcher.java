package com.alex.messenger.message.scheduled;

import com.alex.messenger.message.MessageService;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ScheduledMessageDispatcher {

    private final MessageService messageService;

    @Scheduled(fixedDelayString = "${alex.scheduled-messages.dispatch-interval-ms:2000}")
    @Transactional
    void dispatchDueMessages() {
        List<ScheduledMessageEntity> dueMessages = messageService.lockDueScheduledMessages(Instant.now(), 20);
        for (ScheduledMessageEntity dueMessage : dueMessages) {
            messageService.dispatchScheduledMessage(dueMessage);
        }
    }
}
