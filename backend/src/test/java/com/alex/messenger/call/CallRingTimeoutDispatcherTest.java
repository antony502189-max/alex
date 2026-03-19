package com.alex.messenger.call;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CallRingTimeoutDispatcherTest {

    @Mock
    private CallService callService;

    @Test
    void expireTimedOutCallsDelegatesToService() {
        CallRingTimeoutDispatcher dispatcher = new CallRingTimeoutDispatcher(callService);

        dispatcher.expireTimedOutCalls();

        verify(callService).expireStaleRingingCalls();
    }
}
