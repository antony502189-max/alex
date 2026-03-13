package com.alex.messenger.premium.dto;

import java.time.Instant;
import java.util.UUID;

public record PremiumProfileResponse(
        UUID userId,
        String tier,
        boolean active,
        Instant activeUntil,
        UUID customEmojiStatusId,
        String customEmojiStatusEmoji,
        String customEmojiStatusLabel
) {
}
