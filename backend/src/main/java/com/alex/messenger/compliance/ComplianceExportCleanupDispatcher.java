package com.alex.messenger.compliance;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ComplianceExportCleanupDispatcher {

    private final ComplianceService complianceService;

    @Scheduled(fixedDelayString = "${alex.compliance.export.cleanup-dispatch-interval-ms:60000}")
    void purgeExpiredArtifacts() {
        complianceService.deleteExpiredArtifacts(Instant.now());
    }
}
