package com.alex.messenger.compliance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.alex.messenger.compliance.dto.ComplianceCaseExportArtifactResponse;
import com.alex.messenger.compliance.dto.ComplianceCaseExportDownloadAuditResponse;
import com.alex.messenger.feature.FeatureFlagService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ComplianceControllerTest {

    @Mock
    private ComplianceService complianceService;

    @Mock
    private FeatureFlagService featureFlagService;

    private ComplianceController complianceController;

    @BeforeEach
    void setUp() {
        complianceController = new ComplianceController(complianceService, featureFlagService);
    }

    @Test
    void listExportsReturnsArtifactMetadataForCase() {
        UUID caseId = UUID.randomUUID();
        ComplianceCaseExportArtifactResponse artifact = new ComplianceCaseExportArtifactResponse(
                UUID.randomUUID(),
                "operator-c",
                Instant.parse("2026-03-11T10:00:00Z"),
                5,
                "checksum-1",
                "application/vnd.alex.compliance-export+json",
                Instant.parse("2026-03-12T10:00:00Z"),
                1,
                Instant.parse("2026-03-11T11:00:00Z"),
                "operator-d"
        );
        when(complianceService.listArtifacts("operator-a", caseId)).thenReturn(List.of(artifact));

        ResponseEntity<List<ComplianceCaseExportArtifactResponse>> response =
                complianceController.listExports("operator-a", caseId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(artifact);
        verify(featureFlagService).requireAdminComplianceEnabled();
        verify(complianceService).listArtifacts("operator-a", caseId);
    }

    @Test
    void exportMetadataRejectsBlankOperatorId() {
        assertThatThrownBy(() -> complianceController.exportMetadata("   ", UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(throwable -> ((ResponseStatusException) throwable).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(featureFlagService).requireAdminComplianceEnabled();
        verifyNoInteractions(complianceService);
    }

    @Test
    void listArtifactDownloadAuditsUsesComplianceService() {
        UUID caseId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        ComplianceCaseExportDownloadAuditResponse audit = new ComplianceCaseExportDownloadAuditResponse(
                UUID.randomUUID(),
                artifactId,
                caseId,
                "operator-z",
                Instant.parse("2026-03-11T12:00:00Z"),
                true
        );
        when(complianceService.listArtifactDownloadAudits("operator-a", caseId, artifactId)).thenReturn(List.of(audit));

        ResponseEntity<List<ComplianceCaseExportDownloadAuditResponse>> response =
                complianceController.listArtifactDownloadAudits("operator-a", caseId, artifactId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(audit);
        verify(featureFlagService).requireAdminComplianceEnabled();
        verify(complianceService).listArtifactDownloadAudits("operator-a", caseId, artifactId);
    }
}
