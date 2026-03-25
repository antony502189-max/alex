package com.alex.messenger.sync;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.alex.messenger.chat.ChatService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UnreadStateReconciliationDispatcherTest {

    @Mock
    private UserSyncService userSyncService;

    @Mock
    private ChatService chatService;

    private SyncProperties syncProperties;
    private UnreadStateReconciliationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        syncProperties = new SyncProperties();
        syncProperties.getReconciliation().setBatchSize(2);
        dispatcher = new UnreadStateReconciliationDispatcher(userSyncService, chatService, syncProperties);
    }

    @Test
    void reconcileUnreadStatesSkipsWhenDisabled() {
        syncProperties.getReconciliation().setEnabled(false);

        dispatcher.reconcileUnreadStates();

        verifyNoInteractions(userSyncService, chatService);
    }

    @Test
    void reconcileUnreadStatesRecalculatesRecentChats() {
        UUID firstChatId = UUID.randomUUID();
        UUID secondChatId = UUID.randomUUID();
        when(userSyncService.listChatIdsForUnreadReconciliation(any(), eq(2)))
                .thenReturn(List.of(firstChatId, secondChatId));

        dispatcher.reconcileUnreadStates();

        verify(chatService).reconcileUnreadState(firstChatId);
        verify(chatService).reconcileUnreadState(secondChatId);
    }
}
