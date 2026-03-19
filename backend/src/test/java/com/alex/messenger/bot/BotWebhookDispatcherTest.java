package com.alex.messenger.bot;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BotWebhookDispatcherTest {

    @Mock
    private BotUpdateService botUpdateService;

    @Test
    void dispatchWebhookUpdatesDelegatesToServiceForEachUpdate() {
        BotUpdateEntity first = new BotUpdateEntity();
        first.setId(1L);
        BotUpdateEntity second = new BotUpdateEntity();
        second.setId(2L);

        when(botUpdateService.lockWebhookDeliveryBatch(20)).thenReturn(List.of(first, second));

        BotWebhookDispatcher dispatcher = new BotWebhookDispatcher(botUpdateService);
        dispatcher.dispatchWebhookUpdates();

        verify(botUpdateService).deliverWebhookUpdate(first);
        verify(botUpdateService).deliverWebhookUpdate(second);
    }

    @Test
    void dispatchWebhookUpdatesContinuesWhenSingleDeliveryFails() {
        BotUpdateEntity first = new BotUpdateEntity();
        first.setId(1L);
        BotUpdateEntity second = new BotUpdateEntity();
        second.setId(2L);

        when(botUpdateService.lockWebhookDeliveryBatch(20)).thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("boom")).when(botUpdateService).deliverWebhookUpdate(first);

        BotWebhookDispatcher dispatcher = new BotWebhookDispatcher(botUpdateService);
        dispatcher.dispatchWebhookUpdates();

        verify(botUpdateService).deliverWebhookUpdate(first);
        verify(botUpdateService).deliverWebhookUpdate(second);
    }
}
