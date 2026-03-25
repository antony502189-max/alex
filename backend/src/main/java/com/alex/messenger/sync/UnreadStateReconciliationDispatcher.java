package com.alex.messenger.sync;

import com.alex.messenger.chat.ChatService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UnreadStateReconciliationDispatcher {

    private final UserSyncService userSyncService;
    private final ChatService chatService;
    private final SyncProperties syncProperties;

    @Scheduled(fixedDelayString = "${alex.sync.reconciliation.dispatch-interval-ms:60000}")
    void reconcileUnreadStates() {
        if (!syncProperties.getReconciliation().isEnabled()) {
            return;
        }

        Instant createdAfter = Instant.now().minus(syncProperties.getReconciliation().getLookback());
        List<UUID> chatIds = userSyncService.listChatIdsForUnreadReconciliation(
                createdAfter,
                syncProperties.getReconciliation().getBatchSize()
        );
        for (UUID chatId : chatIds) {
            chatService.reconcileUnreadState(chatId);
        }
    }
}
