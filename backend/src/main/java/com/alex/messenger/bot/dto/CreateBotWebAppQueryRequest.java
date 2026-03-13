package com.alex.messenger.bot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateBotWebAppQueryRequest(
        @NotBlank String initData,
        @NotBlank String signature,
        @Size(max = 4000) String queryText
) {
}
