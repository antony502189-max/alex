package com.alex.messenger.account;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccountDeletionDispatcher {

    private final AccountService accountService;

    @Scheduled(fixedDelayString = "${alex.account.deletion.dispatch-interval-ms:60000}")
    void processDeletionQueue() {
        accountService.scheduleInactiveDeletionJobs();
        accountService.executeDueDeletions();
    }
}
