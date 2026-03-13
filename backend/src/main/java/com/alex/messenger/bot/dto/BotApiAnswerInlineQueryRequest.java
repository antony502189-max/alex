package com.alex.messenger.bot.dto;

import jakarta.validation.Valid;
import java.util.List;

public record BotApiAnswerInlineQueryRequest(
        String query,
        Integer cacheTimeSeconds,
        @Valid List<BotApiInlineResultRequest> results
) {
}
