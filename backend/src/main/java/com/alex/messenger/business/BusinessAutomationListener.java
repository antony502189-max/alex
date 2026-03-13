package com.alex.messenger.business;

import com.alex.messenger.message.DirectMessageCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class BusinessAutomationListener {

    private final BusinessAutomationService businessAutomationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDirectMessageCreated(DirectMessageCreatedEvent event) {
        businessAutomationService.handleIncomingDirectMessage(
                event.chatId(),
                event.senderId(),
                event.createdAt()
        );
    }
}
