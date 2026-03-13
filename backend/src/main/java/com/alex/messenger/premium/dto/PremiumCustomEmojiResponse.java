package com.alex.messenger.premium.dto;

import java.util.UUID;

public record PremiumCustomEmojiResponse(
        UUID customEmojiId,
        String shortCode,
        String emoji,
        String label,
        boolean premiumRequired
) {
}
