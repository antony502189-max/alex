package com.alex.messenger.premium.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SendPremiumGiftRequest(
        @NotNull UUID recipientUserId,
        UUID customEmojiId,
        @Size(max = 255) String message,
        Integer premiumDaysGranted
) {
}
