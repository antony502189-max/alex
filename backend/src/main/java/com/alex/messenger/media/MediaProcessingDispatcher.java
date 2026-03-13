package com.alex.messenger.media;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MediaProcessingDispatcher {

    private final MediaProcessingService mediaProcessingService;

    @Scheduled(fixedDelayString = "${alex.media.processing.dispatch-interval-ms:60000}")
    void processPendingJobs() {
        mediaProcessingService.processPendingJobs(Instant.now());
    }
}
