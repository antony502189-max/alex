package com.alex.messenger.bot.dto;

import java.util.List;

public record BotUpdatesResponse(
        List<BotUpdateResponse> updates,
        long nextOffset
) {
}
