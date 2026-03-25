package com.alex.messenger.compliance;

import com.alex.messenger.compliance.dto.ComplianceCaseApproveRequest;
import com.alex.messenger.compliance.dto.ComplianceCaseArtifactDownloadResponse;
import com.alex.messenger.compliance.dto.ComplianceCaseCreateRequest;
import com.alex.messenger.compliance.dto.ComplianceCaseExportArtifactResponse;
import com.alex.messenger.compliance.dto.ComplianceCaseExportDownloadAuditResponse;
import com.alex.messenger.compliance.dto.ComplianceCaseExportResponse;
import com.alex.messenger.compliance.dto.ComplianceCaseResponse;
import com.alex.messenger.feature.FeatureFlagService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/compliance/cases")
@RequiredArgsConstructor
public class ComplianceController {

    public static final String OPERATOR_ID_HEADER = "X-Operator-Id";

    private final ComplianceService complianceService;
    private final FeatureFlagService featureFlagService;

    @PostMapping
    public ResponseEntity<ComplianceCaseResponse> createCase(
            @RequestHeader(OPERATOR_ID_HEADER) String operatorId,
            @Valid @RequestBody ComplianceCaseCreateRequest request
    ) {
        featureFlagService.requireAdminComplianceEnabled();
        return ResponseEntity.status(HttpStatus.CREATED).body(complianceService.createCase(operatorId, request));
    }

    @GetMapping("/{caseId}")
    public ResponseEntity<ComplianceCaseResponse> getCase(
            @RequestHeader(OPERATOR_ID_HEADER) String operatorId,
            @PathVariable UUID caseId
    ) {
        featureFlagService.requireAdminComplianceEnabled();
        if (operatorId == null || operatorId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Operator id is required");
        }
        return ResponseEntity.ok(complianceService.getCase(operatorId, caseId));
    }

    @PostMapping("/{caseId}/approve")
    public ResponseEntity<ComplianceCaseResponse> approveCase(
            @RequestHeader(OPERATOR_ID_HEADER) String operatorId,
            @PathVariable UUID caseId,
            @Valid @RequestBody(required = false) ComplianceCaseApproveRequest request
    ) {
        featureFlagService.requireAdminComplianceEnabled();
        return ResponseEntity.ok(complianceService.approveCase(
                operatorId,
                caseId,
                request != null ? request : new ComplianceCaseApproveRequest(null)
        ));
    }

    @PostMapping("/{caseId}/exports")
    public ResponseEntity<ComplianceCaseExportResponse> exportCase(
            @RequestHeader(OPERATOR_ID_HEADER) String operatorId,
            @PathVariable UUID caseId
    ) {
        featureFlagService.requireAdminComplianceEnabled();
        return ResponseEntity.ok(complianceService.exportCase(operatorId, caseId));
    }

    @GetMapping("/{caseId}/exports")
    public ResponseEntity<List<ComplianceCaseExportArtifactResponse>> listExports(
            @RequestHeader(OPERATOR_ID_HEADER) String operatorId,
            @PathVariable UUID caseId
    ) {
        featureFlagService.requireAdminComplianceEnabled();
        if (operatorId == null || operatorId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Operator id is required");
        }
        return ResponseEntity.ok(complianceService.listArtifacts(operatorId, caseId));
    }

    @GetMapping("/{caseId}/exports/latest/metadata")
    public ResponseEntity<ComplianceCaseExportArtifactResponse> latestExportMetadata(
            @RequestHeader(OPERATOR_ID_HEADER) String operatorId,
            @PathVariable UUID caseId
    ) {
        featureFlagService.requireAdminComplianceEnabled();
        if (operatorId == null || operatorId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Operator id is required");
        }
        return ResponseEntity.ok(complianceService.getLatestArtifactMetadata(operatorId, caseId));
    }

    @GetMapping("/{caseId}/exports/{artifactId}/metadata")
    public ResponseEntity<ComplianceCaseExportArtifactResponse> exportMetadata(
            @RequestHeader(OPERATOR_ID_HEADER) String operatorId,
            @PathVariable UUID caseId,
            @PathVariable UUID artifactId
    ) {
        featureFlagService.requireAdminComplianceEnabled();
        if (operatorId == null || operatorId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Operator id is required");
        }
        return ResponseEntity.ok(complianceService.getArtifactMetadata(operatorId, caseId, artifactId));
    }

    @GetMapping("/{caseId}/exports/latest")
    public ResponseEntity<ComplianceCaseArtifactDownloadResponse> downloadLatestExport(
            @RequestHeader(OPERATOR_ID_HEADER) String operatorId,
            @PathVariable UUID caseId
    ) {
        featureFlagService.requireAdminComplianceEnabled();
        return ResponseEntity.ok(complianceService.downloadLatestArtifact(operatorId, caseId));
    }

    @GetMapping("/{caseId}/exports/{artifactId}")
    public ResponseEntity<ComplianceCaseArtifactDownloadResponse> downloadExport(
            @RequestHeader(OPERATOR_ID_HEADER) String operatorId,
            @PathVariable UUID caseId,
            @PathVariable UUID artifactId
    ) {
        featureFlagService.requireAdminComplianceEnabled();
        return ResponseEntity.ok(complianceService.downloadArtifact(operatorId, caseId, artifactId));
    }

    @GetMapping("/{caseId}/exports/downloads")
    public ResponseEntity<List<ComplianceCaseExportDownloadAuditResponse>> listDownloadAudits(
            @RequestHeader(OPERATOR_ID_HEADER) String operatorId,
            @PathVariable UUID caseId
    ) {
        featureFlagService.requireAdminComplianceEnabled();
        if (operatorId == null || operatorId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Operator id is required");
        }
        return ResponseEntity.ok(complianceService.listDownloadAudits(operatorId, caseId));
    }

    @GetMapping("/{caseId}/exports/{artifactId}/downloads")
    public ResponseEntity<List<ComplianceCaseExportDownloadAuditResponse>> listArtifactDownloadAudits(
            @RequestHeader(OPERATOR_ID_HEADER) String operatorId,
            @PathVariable UUID caseId,
            @PathVariable UUID artifactId
    ) {
        featureFlagService.requireAdminComplianceEnabled();
        if (operatorId == null || operatorId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Operator id is required");
        }
        return ResponseEntity.ok(complianceService.listArtifactDownloadAudits(operatorId, caseId, artifactId));
    }
}
