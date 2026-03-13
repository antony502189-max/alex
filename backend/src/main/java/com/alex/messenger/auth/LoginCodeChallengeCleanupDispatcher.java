package com.alex.messenger.auth;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoginCodeChallengeCleanupDispatcher {

    private final AuthService authService;
    private final AuthProperties authProperties;

    @Scheduled(fixedDelayString = "${alex.auth.cleanup.dispatch-interval-ms:60000}")
    void deleteExpiredChallenges() {
        authService.deleteExpiredOrConsumedChallenges(
                Instant.now(),
                authProperties.getCleanup().getBatchSize()
        );
        authService.deleteExpiredOrConsumedTwoFactorChallenges(
                Instant.now(),
                authProperties.getCleanup().getBatchSize()
        );
        authService.deleteExpiredOrFinishedQrChallenges(
                Instant.now(),
                authProperties.getCleanup().getBatchSize()
        );
    }
}
