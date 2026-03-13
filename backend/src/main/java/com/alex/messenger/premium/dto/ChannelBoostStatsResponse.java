package com.alex.messenger.premium.dto;

import java.util.List;
import java.util.UUID;

public record ChannelBoostStatsResponse(
        UUID channelChatId,
        int totalBoosts,
        int uniqueBoosters,
        int viewerBoostCount,
        List<ChannelBoostResponse> boosts
) {
}
