package com.alex.messenger.call;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CallRingTimeoutDispatcher {

    private final CallService callService;

    @Scheduled(fixedDelayString = "${alex.calls.lifecycle.ring-timeout-dispatch-interval-ms:10000}")
    void expireTimedOutCalls() {
        callService.expireStaleRingingCalls();
    }
}
