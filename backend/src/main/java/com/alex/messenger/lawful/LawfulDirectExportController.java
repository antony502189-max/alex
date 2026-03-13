package com.alex.messenger.lawful;

import com.alex.messenger.feature.FeatureFlagService;
import com.alex.messenger.lawful.dto.DirectLawfulExportRequest;
import com.alex.messenger.lawful.dto.DirectLawfulExportResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/lawful/exports")
@RequiredArgsConstructor
public class LawfulDirectExportController {

    public static final String OPERATOR_ID_HEADER = "X-Operator-Id";

    private final FeatureFlagService featureFlagService;
    private final LawfulDirectExportService lawfulDirectExportService;

    @PostMapping("/direct")
    public ResponseEntity<DirectLawfulExportResponse> exportDirect(
            @RequestHeader(OPERATOR_ID_HEADER) String operatorId,
            @Valid @RequestBody DirectLawfulExportRequest request
    ) {
        featureFlagService.requireLawfulDirectExportEnabled();
        return ResponseEntity.status(HttpStatus.CREATED).body(lawfulDirectExportService.export(operatorId, request));
    }
}
