package com.alex.messenger.monetization;

import com.alex.messenger.feature.FeatureFlagService;
import com.alex.messenger.monetization.dto.MonetizationProviderReconciliationRequest;
import com.alex.messenger.monetization.dto.MonetizationProviderSyncRunResponse;
import com.alex.messenger.monetization.dto.MonetizationWithdrawalProviderCallbackRequest;
import com.alex.messenger.monetization.dto.MonetizationWithdrawalProviderCallbackResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/monetization")
@RequiredArgsConstructor
public class MonetizationInternalController {

    private final FeatureFlagService featureFlagService;
    private final MonetizationService monetizationService;

    @PostMapping("/withdrawals/provider-callbacks")
    public ResponseEntity<MonetizationWithdrawalProviderCallbackResponse> handleProviderCallback(
            @Valid @RequestBody MonetizationWithdrawalProviderCallbackRequest request
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(monetizationService.applyProviderCallback(request));
    }

    @PostMapping("/channels/{chatId}/provider-reconciliation")
    public ResponseEntity<MonetizationProviderSyncRunResponse> reconcileProviderStatuses(
            @PathVariable java.util.UUID chatId,
            @Valid @RequestBody MonetizationProviderReconciliationRequest request
    ) {
        featureFlagService.requireMonetizationEnabled();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(monetizationService.reconcileProviderStatuses(chatId, request));
    }
}
