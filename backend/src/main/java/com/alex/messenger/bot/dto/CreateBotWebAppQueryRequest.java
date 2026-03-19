package com.alex.messenger.bot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateBotWebAppQueryRequest(
        @NotBlank @Size(max = 8192) String initData,
        @NotBlank @Size(max = 512) String signature,
        @Size(max = 4000) String queryText
) {
}
