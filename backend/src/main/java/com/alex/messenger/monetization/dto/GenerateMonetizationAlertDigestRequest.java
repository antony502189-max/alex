package com.alex.messenger.monetization.dto;

import java.util.UUID;

public record GenerateMonetizationAlertDigestRequest(
        UUID targetChatId,
        String note
) {
}
