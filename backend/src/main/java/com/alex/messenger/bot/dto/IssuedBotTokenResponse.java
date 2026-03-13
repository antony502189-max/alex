package com.alex.messenger.bot.dto;

public record IssuedBotTokenResponse(
        DeveloperBotResponse bot,
        String apiToken
) {
}
