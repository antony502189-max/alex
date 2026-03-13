package com.alex.messenger.attachment;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AttachmentUploadSessionCleanupDispatcher {

    private final AttachmentUploadSessionService attachmentUploadSessionService;

    @Scheduled(fixedDelayString = "${alex.storage.attachments.resumable.cleanup-dispatch-interval-ms:60000}")
    void deleteExpiredUploadSessions() {
        attachmentUploadSessionService.deleteExpiredSessions(Instant.now());
    }
}
