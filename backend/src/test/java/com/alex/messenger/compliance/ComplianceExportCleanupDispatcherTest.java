package com.alex.messenger.compliance;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ComplianceExportCleanupDispatcherTest {

    @Mock
    private ComplianceService complianceService;

    @Test
    void purgeExpiredArtifactsDelegatesToComplianceService() {
        ComplianceExportCleanupDispatcher dispatcher = new ComplianceExportCleanupDispatcher(complianceService);

        dispatcher.purgeExpiredArtifacts();

        verify(complianceService).deleteExpiredArtifacts(any(Instant.class));
    }
}
