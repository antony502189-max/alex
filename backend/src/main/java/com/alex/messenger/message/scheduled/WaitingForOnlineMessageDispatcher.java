package com.alex.messenger.message.scheduled;

import com.alex.messenger.message.MessageService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class WaitingForOnlineMessageDispatcher {

    private final MessageService messageService;

    @Scheduled(fixedDelayString = "${alex.send-when-online.dispatch-interval-ms:5000}")
    @Transactional
    void dispatchMessagesWhenRecipientComesOnline() {
        List<ScheduledMessageEntity> waitingMessages = messageService.lockWaitingForOnlineMessages(20);
        for (ScheduledMessageEntity waitingMessage : waitingMessages) {
            messageService.dispatchWhenRecipientOnline(waitingMessage);
        }
    }
}
