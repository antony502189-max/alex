package com.alex.messenger.auth;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PasskeyChallengeCleanupDispatcher {

    private final PasskeyService passkeyService;
    private final AuthProperties authProperties;

    @Scheduled(fixedDelayString = "${alex.auth.cleanup.dispatch-interval-ms:60000}")
    void deleteExpiredChallenges() {
        passkeyService.deleteExpiredChallenges(Instant.now(), authProperties.getCleanup().getBatchSize());
    }
}
