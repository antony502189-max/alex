package com.alex.messenger.monetization.dto;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

public record MonetizationProviderReconciliationRequest(
        @Valid List<MonetizationProviderStatusUpdateRequest> updates,
        UUID publishArtifactToChatId,
        String note
) {
}
