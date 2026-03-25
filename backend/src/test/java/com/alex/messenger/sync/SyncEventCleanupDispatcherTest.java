package com.alex.messenger.sync;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SyncEventCleanupDispatcherTest {

    @Mock
    private UserSyncService userSyncService;

    private SyncProperties syncProperties;
    private SyncEventCleanupDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        syncProperties = new SyncProperties();
        dispatcher = new SyncEventCleanupDispatcher(userSyncService, syncProperties);
    }

    @Test
    void cleanupExpiredEventsDelegatesToServiceWhenEnabled() {
        syncProperties.getRetention().setEnabled(true);
        syncProperties.getRetention().setCleanupBatchSize(25);

        dispatcher.cleanupExpiredEvents();

        verify(userSyncService).deleteExpiredEvents(any(), eq(25));
    }

    @Test
    void cleanupExpiredEventsSkipsWhenDisabled() {
        syncProperties.getRetention().setEnabled(false);

        dispatcher.cleanupExpiredEvents();

        verify(userSyncService, never()).deleteExpiredEvents(any(), anyInt());
    }
}
