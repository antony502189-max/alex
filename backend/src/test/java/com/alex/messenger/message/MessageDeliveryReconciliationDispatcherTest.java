package com.alex.messenger.message;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MessageDeliveryReconciliationDispatcherTest {

    @Mock
    private MessageDeliveryService messageDeliveryService;

    private MessageDeliveryReconciliationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new MessageDeliveryReconciliationDispatcher(messageDeliveryService);
    }

    @Test
    void reconcileRecentDirectChatsDelegatesToService() {
        dispatcher.reconcileRecentDirectChats();

        verify(messageDeliveryService).reconcileRecentDirectChats();
    }
}
