package com.alex.messenger.monetization.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record MonetizationProviderReconciliationRequest(
        List<@NotNull @Valid MonetizationProviderStatusUpdateRequest> updates,
        UUID publishArtifactToChatId,
        String note
) {
}
