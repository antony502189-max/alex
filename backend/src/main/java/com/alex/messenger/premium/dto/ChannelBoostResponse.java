package com.alex.messenger.premium.dto;

import java.time.Instant;
import java.util.UUID;

public record ChannelBoostResponse(
        UUID channelChatId,
        UUID boostedByUserId,
        int boostCount,
        Instant updatedAt
) {
}
