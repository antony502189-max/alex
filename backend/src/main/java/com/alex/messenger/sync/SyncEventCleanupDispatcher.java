package com.alex.messenger.sync;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SyncEventCleanupDispatcher {

    private final UserSyncService userSyncService;
    private final SyncProperties syncProperties;

    @Scheduled(fixedDelayString = "${alex.sync.retention.cleanup-dispatch-interval-ms:60000}")
    void cleanupExpiredEvents() {
        if (!syncProperties.getRetention().isEnabled()) {
            return;
        }
        userSyncService.deleteExpiredEvents(
                Instant.now().minus(syncProperties.getRetention().getTtl()),
                syncProperties.getRetention().getCleanupBatchSize()
        );
    }
}
