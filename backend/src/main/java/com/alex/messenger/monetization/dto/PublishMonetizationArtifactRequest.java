package com.alex.messenger.monetization.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PublishMonetizationArtifactRequest(
        @NotNull UUID targetChatId,
        String note
) {
}
