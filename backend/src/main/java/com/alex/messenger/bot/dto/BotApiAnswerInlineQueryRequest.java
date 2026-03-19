package com.alex.messenger.bot.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BotApiAnswerInlineQueryRequest(
        @Size(max = 512) String query,
        @PositiveOrZero @Max(3600) Integer cacheTimeSeconds,
        @Size(max = 50) List<@NotNull @Valid BotApiInlineResultRequest> results
) {
}
