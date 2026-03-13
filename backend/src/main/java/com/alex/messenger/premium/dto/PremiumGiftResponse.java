package com.alex.messenger.premium.dto;

import java.time.Instant;
import java.util.UUID;

public record PremiumGiftResponse(
        UUID giftId,
        UUID senderUserId,
        String senderDisplayName,
        UUID recipientUserId,
        String recipientDisplayName,
        UUID customEmojiId,
        String customEmoji,
        String customEmojiLabel,
        String message,
        int premiumDaysGranted,
        Instant createdAt
) {
}
